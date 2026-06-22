package com.zayan.locationtracker.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.zayan.locationtracker.database.entity.LocationEntity;
import com.zayan.locationtracker.repository.LocationRepository;
import com.zayan.locationtracker.utils.Constants;

import java.util.List;

/**
 * ViewModel for the LocationTracker app.
 *
 * Shared across MainActivity, HistoryActivity, and indirectly SettingsActivity.
 * Survives configuration changes (rotation, theme switch) — the repository
 * and all LiveData state are preserved without re-fetching from the database.
 *
 * Rules enforced here:
 *  - No Context references (use AndroidViewModel if context is ever needed)
 *  - MutableLiveData is private; public interface is read-only LiveData
 *  - No business logic that belongs in the Repository
 *  - No direct DAO access — always goes through the Repository
 */
public class LocationViewModel extends ViewModel {

    private final LocationRepository repository;

    // ─── Tracking State ──────────────────────────────────────────────────────

    /**
     * Whether location tracking is currently active.
     * Observed by MainActivity to update button states and status chip.
     * Updated by the service (via broadcast) and by user actions.
     */
    private final MutableLiveData<Boolean> isTracking = new MutableLiveData<>(false);

    /**
     * The active location update interval in milliseconds.
     * Default matches Constants.DEFAULT_INTERVAL_MS.
     * Updated when the user changes the setting — the service reads this
     * to reconfigure FusedLocationProviderClient in place.
     */
    private final MutableLiveData<Long> currentIntervalMs =
            new MutableLiveData<>(Constants.DEFAULT_INTERVAL_MS);

    /**
     * The most recent location update received from the service.
     * Observed by MainActivity to display current lat/lon/time.
     * Null until the first location fix arrives.
     */
    private final MutableLiveData<LocationEntity> latestLocation = new MutableLiveData<>(null);

    /**
     * A one-shot status message for the UI — error conditions, GPS disabled,
     * permission denial explanations. Consumed once, then cleared to null.
     * The Activity observes this to show Snackbars or dialogs.
     */
    private final MutableLiveData<String> statusMessage = new MutableLiveData<>(null);

    // ─── Database-backed LiveData ─────────────────────────────────────────────

    /**
     * The complete location history, newest first.
     * Backed directly by Room's LiveData — auto-updates when the table changes.
     * Observed by HistoryActivity's RecyclerView adapter.
     */
    private final LiveData<List<LocationEntity>> locationHistory;

    // ─── Constructor ─────────────────────────────────────────────────────────

    /**
     * Package-private constructor — instantiated only by LocationViewModelFactory.
     * Never construct directly; always go through ViewModelProvider.
     *
     * @param repository The shared repository instance.
     */
    LocationViewModel(@NonNull LocationRepository repository) {
        this.repository = repository;
        // Wire up the Room LiveData immediately — this doesn't execute a query
        // until an observer subscribes, so no wasted work on construction.
        this.locationHistory = repository.getAllLocations();

        // Seed latestLocation from the database on construction.
        // This ensures the "Last Known Location" card survives rotation —
        // the ViewModel lives across config changes but MutableLiveData
        // initialized to null loses the last value on re-subscription without this.
        loadLatestLocationFromDb();
    }

    /**
     * Query the database for the most recent location record and populate
     * latestLocation LiveData. Called once in the constructor.
     * Uses the repository's background executor — safe to call from any thread.
     */
    private void loadLatestLocationFromDb() {
        repository.getLatestLocation(location -> {
            if (location != null) {
                // postValue is thread-safe — repository callback runs on bg thread.
                latestLocation.postValue(location);
            }
        });
    }

    // ─── Public LiveData Accessors (read-only) ────────────────────────────────

    /** @return LiveData tracking whether the location service is running. */
    public LiveData<Boolean> isTracking() {
        return isTracking;
    }

    /** @return LiveData of the current update interval in milliseconds. */
    public LiveData<Long> getCurrentIntervalMs() {
        return currentIntervalMs;
    }

    /** @return LiveData of the most recent location fix. Null before first fix. */
    public LiveData<LocationEntity> getLatestLocation() {
        return latestLocation;
    }

    /**
     * One-shot status messages for the UI (errors, warnings, guidance).
     * The Activity should clear this after consuming it to prevent
     * re-showing the same message on rotation.
     *
     * @return LiveData delivering status strings as they arise.
     */
    public LiveData<String> getStatusMessage() {
        return statusMessage;
    }

    /**
     * Full location history from the database, newest first.
     * Room keeps this automatically in sync — no manual refresh needed.
     *
     * @return LiveData list of all recorded LocationEntity objects.
     */
    public LiveData<List<LocationEntity>> getLocationHistory() {
        return locationHistory;
    }

    // ─── State Mutators (called by Activities and Service callbacks) ──────────

    /**
     * Update the tracking running state.
     * Called by MainActivity when the service starts/stops,
     * and by the service's status broadcast.
     *
     * @param tracking true if service is actively tracking, false otherwise.
     */
    public void setTracking(boolean tracking) {
        isTracking.setValue(tracking);
    }

    /**
     * Post a tracking state update from a background thread.
     * Safe to call from the service's location callback thread.
     *
     * @param tracking true if service is actively tracking.
     */
    public void postTracking(boolean tracking) {
        isTracking.postValue(tracking);
    }

    /**
     * Update the current interval. Called when the user changes the setting.
     * Must be called on the main thread (e.g. from SettingsActivity's onResume).
     *
     * @param intervalMs New interval in milliseconds.
     */
    public void setCurrentIntervalMs(long intervalMs) {
        currentIntervalMs.setValue(intervalMs);
    }

    /**
     * Post the latest received location fix.
     * Called from the service on a background callback thread.
     *
     * @param location The most recent LocationEntity after it has been saved.
     */
    public void postLatestLocation(@NonNull LocationEntity location) {
        latestLocation.postValue(location);
    }

    /**
     * Post a status message for display in the UI.
     * Called from any thread — service, permission handler, GPS checker.
     *
     * @param message Human-readable status or error message.
     */
    public void postStatusMessage(@NonNull String message) {
        statusMessage.postValue(message);
    }

    /**
     * Clear the status message after the UI has consumed it.
     * Prevents the same message from reappearing after rotation.
     * Call this from the Activity after showing the Snackbar/dialog.
     */
    public void clearStatusMessage() {
        statusMessage.setValue(null);
    }

    // ─── Repository Operations ────────────────────────────────────────────────

    /**
     * Insert a location record into the database.
     * Delegates to repository which handles background threading.
     *
     * @param location The entity to persist.
     */
    public void insertLocation(@NonNull LocationEntity location) {
        repository.insert(location);
    }

    /**
     * Delete all location records from history.
     * The LiveData returned by getLocationHistory() updates automatically.
     */
    public void clearHistory() {
        repository.deleteAll();
    }

    /**
     * Delete a single location record.
     *
     * @param location The entity to remove.
     */
    public void deleteLocation(@NonNull LocationEntity location) {
        repository.delete(location);
    }

    // ─── Convenience Helpers ─────────────────────────────────────────────────

    /**
     * Convenience check — whether the service is currently tracked as running.
     * Returns the current value synchronously (not a LiveData subscription).
     * Useful for one-off checks in onClick handlers.
     *
     * @return true if tracking is active, false if not or if state is unknown.
     */
    public boolean isCurrentlyTracking() {
        Boolean val = isTracking.getValue();
        return val != null && val;
    }

    /**
     * Returns the current interval value synchronously.
     * Falls back to the default if LiveData value is null (shouldn't happen,
     * but defensive code here prevents a NullPointerException in the service).
     *
     * @return Current interval in milliseconds.
     */
    public long getCurrentIntervalMsValue() {
        Long val = currentIntervalMs.getValue();
        return val != null ? val : Constants.DEFAULT_INTERVAL_MS;
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    /**
     * Called when the ViewModel is about to be destroyed — the Activity
     * is finishing (not rotating). Clean up anything that wouldn't be
     * cleaned up automatically.
     *
     * LiveData observers are lifecycle-aware and clean themselves up.
     * The repository's executor is a singleton and should NOT be shut down here.
     */
    @Override
    protected void onCleared() {
        super.onCleared();
        // No manual cleanup needed currently — LiveData handles observer lifecycle.
        // If we ever add RxJava CompositeDisposable or manual listeners,
        // release them here to prevent memory leaks.
    }
}
