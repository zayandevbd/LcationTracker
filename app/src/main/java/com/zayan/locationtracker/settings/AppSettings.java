package com.zayan.locationtracker.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.zayan.locationtracker.utils.Constants;

/**
 * Centralised wrapper around SharedPreferences for all app settings.
 *
 * No other class should call getSharedPreferences() directly — all reads
 * and writes go through this singleton. This keeps key strings in one place
 * (Constants.java) and makes a future migration to DataStore a single-file change.
 *
 * All methods are safe to call from any thread — SharedPreferences reads are
 * in-memory after the first load, and we use apply() (async) for writes.
 */
public class AppSettings {

    private static final String TAG = Constants.LOG_TAG + "/AppSettings";

    private static volatile AppSettings instance;

    private final SharedPreferences prefs;

    // ─── Singleton ───────────────────────────────────────────────────────────

    private AppSettings(@NonNull Context context) {
        // Always use application context — this singleton outlives any Activity.
        prefs = context.getApplicationContext()
                .getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Returns the singleton AppSettings instance.
     * Thread-safe via double-checked locking.
     *
     * @param context Any context — application context extracted internally.
     */
    public static AppSettings getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (AppSettings.class) {
                if (instance == null) {
                    instance = new AppSettings(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    // ─── Location Interval ───────────────────────────────────────────────────

    /**
     * Get the saved location update interval in milliseconds.
     *
     * If no value is stored yet (first launch), returns the default interval.
     * If the stored value is not in the allowed options list (e.g. corrupted
     * prefs or an app downgrade), it also falls back to the default. This
     * prevents the service from running at an unexpected interval.
     *
     * @return Interval in milliseconds — always a valid option from Constants.
     */
    public long getIntervalMs() {
        long stored = prefs.getLong(Constants.PREF_KEY_INTERVAL_MS, Constants.DEFAULT_INTERVAL_MS);

        // Validate stored value is one of the allowed options.
        for (long option : Constants.INTERVAL_OPTIONS_MS) {
            if (stored == option) {
                return stored;
            }
        }

        // Stored value is not a known option — reset to default and log it.
        Log.w(TAG, "getIntervalMs: stored value " + stored +
                "ms is not a valid option. Resetting to default.");
        setIntervalMs(Constants.DEFAULT_INTERVAL_MS);
        return Constants.DEFAULT_INTERVAL_MS;
    }

    /**
     * Save the location update interval.
     *
     * Uses apply() — asynchronous, non-blocking. Safe to call on the main thread.
     * The setting takes effect immediately: the service reads this value the next
     * time it processes an interval update or restarts.
     *
     * @param intervalMs Interval in milliseconds. Should be one of INTERVAL_OPTIONS_MS.
     */
    public void setIntervalMs(long intervalMs) {
        prefs.edit()
                .putLong(Constants.PREF_KEY_INTERVAL_MS, intervalMs)
                .apply();
        Log.d(TAG, "setIntervalMs: saved interval " + intervalMs + "ms.");
    }

    /**
     * Returns the index of the current interval within INTERVAL_OPTIONS_MS.
     * Used to pre-select the correct item in the Settings spinner/radio group.
     *
     * Falls back to DEFAULT_INTERVAL_INDEX if the current interval isn't found
     * (shouldn't happen after the validation in getIntervalMs(), but defensive).
     *
     * @return Zero-based index into Constants.INTERVAL_OPTIONS_MS.
     */
    public int getIntervalIndex() {
        long current = getIntervalMs();
        for (int i = 0; i < Constants.INTERVAL_OPTIONS_MS.length; i++) {
            if (Constants.INTERVAL_OPTIONS_MS[i] == current) {
                return i;
            }
        }
        return Constants.DEFAULT_INTERVAL_INDEX;
    }

    // ─── Tracking State Persistence ──────────────────────────────────────────

    /**
     * Persist whether tracking was active.
     *
     * Called by LocationTrackingService when it starts and stops.
     * The BootReceiver reads this on device reboot to decide whether
     * to automatically restart the tracking service.
     *
     * Uses commit() here instead of apply() — this is called from the service's
     * onDestroy(), and we need the write to complete synchronously before the
     * process potentially ends. apply() is async and might not complete in time.
     *
     * @param wasTracking true when service starts, false when it stops.
     */
    public void setWasTracking(boolean wasTracking) {
        prefs.edit()
                .putBoolean(Constants.PREF_KEY_WAS_TRACKING, wasTracking)
                .commit(); // synchronous — called from service lifecycle
        Log.d(TAG, "setWasTracking: " + wasTracking);
    }

    /**
     * Whether tracking was active when the app/device last stopped.
     * Used by BootReceiver and on app resume to restore tracking state.
     *
     * Defaults to false — on a clean install, don't start tracking automatically.
     *
     * @return true if the service was running when it last shut down.
     */
    public boolean wasTracking() {
        return prefs.getBoolean(Constants.PREF_KEY_WAS_TRACKING, false);
    }

    // ─── Utility ─────────────────────────────────────────────────────────────

    /**
     * Clear all stored settings and reset to defaults.
     *
     * Not exposed in the UI currently — available for testing and
     * potential "reset to defaults" feature in a future settings screen.
     * Uses commit() to ensure the clear is synchronous.
     */
    public void resetToDefaults() {
        prefs.edit().clear().commit();
        Log.d(TAG, "resetToDefaults: all settings cleared.");
    }

    /**
     * Human-readable label for the currently selected interval.
     * Used in the main screen status display ("Interval: 5 minutes").
     *
     * @return Label string from Constants.INTERVAL_LABELS.
     */
    @NonNull
    public String getIntervalLabel() {
        int index = getIntervalIndex();
        if (index >= 0 && index < Constants.INTERVAL_LABELS.length) {
            return Constants.INTERVAL_LABELS[index];
        }
        return Constants.INTERVAL_LABELS[Constants.DEFAULT_INTERVAL_INDEX];
    }
}
