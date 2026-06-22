package com.zayan.locationtracker.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.zayan.locationtracker.database.AppDatabase;
import com.zayan.locationtracker.database.dao.LocationDao;
import com.zayan.locationtracker.database.entity.LocationEntity;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository — the single source of truth for location data.
 *
 * Sits between the ViewModel (and Service) and the Room database.
 * Owns all threading: database operations run on a dedicated background
 * executor thread, never on the main thread.
 *
 * Callers (ViewModel, Service) never touch the DAO or AppDatabase directly.
 * If we ever add a remote API or caching layer, only this class changes.
 *
 * Singleton: one instance per app process, shared between the ViewModel
 * and the LocationTrackingService.
 */
public class LocationRepository {

    private static volatile LocationRepository instance;

    private final LocationDao dao;

    /**
     * Single-thread executor for all database writes and one-shot reads.
     *
     * Single thread (not a pool) intentionally — serializes inserts so
     * there's no race between concurrent writes from the service. If we
     * ever need parallel reads, we can add a separate read executor then.
     */
    private final ExecutorService writeExecutor;

    // ─── Singleton ───────────────────────────────────────────────────────────

    private LocationRepository(@NonNull Context context) {
        AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
        this.dao = db.locationDao();
        this.writeExecutor = Executors.newSingleThreadExecutor();
    }

    /**
     * Returns the shared repository instance, creating it on first call.
     * Thread-safe via double-checked locking.
     *
     * @param context Any context — application context extracted internally.
     */
    public static LocationRepository getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (LocationRepository.class) {
                if (instance == null) {
                    instance = new LocationRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    // ─── Callback Interfaces ─────────────────────────────────────────────────

    /**
     * Callback for insert operations.
     * Delivers the new row ID on the background thread — if you need to
     * update UI from this, post to the main thread via Handler or LiveData.
     */
    public interface InsertCallback {
        /**
         * @param rowId The SQLite row ID of the inserted record,
         *              or -1 if the insert was ignored/failed.
         */
        void onInserted(long rowId);
    }

    /**
     * Callback for one-shot queries that return a single entity.
     * The result is delivered on the background thread.
     */
    public interface LocationCallback {
        void onResult(@Nullable LocationEntity location);
    }

    /**
     * Callback for operations that return a count or affected-row number.
     */
    public interface CountCallback {
        void onResult(int count);
    }

    // ─── Write Operations ────────────────────────────────────────────────────

    /**
     * Insert a new location record asynchronously.
     *
     * Dispatched to the background executor — safe to call from any thread
     * including the main thread. The optional callback fires on the same
     * background thread; post to main thread if you need to update UI.
     *
     * @param location The entity to insert. Set id = 0 for auto-generation.
     * @param callback Optional. Receives the new row ID when done. May be null.
     */
    public void insert(@NonNull LocationEntity location, @Nullable InsertCallback callback) {
        writeExecutor.execute(() -> {
            long rowId = dao.insert(location);
            if (callback != null) {
                callback.onInserted(rowId);
            }
        });
    }

    /**
     * Convenience overload — insert without needing the row ID back.
     * Used by the service when it just needs fire-and-forget persistence.
     *
     * @param location The entity to insert.
     */
    public void insert(@NonNull LocationEntity location) {
        insert(location, null);
    }

    /**
     * Delete a specific location record asynchronously.
     *
     * @param location The entity to delete. Must have a valid non-zero id.
     */
    public void delete(@NonNull LocationEntity location) {
        writeExecutor.execute(() -> dao.delete(location));
    }

    /**
     * Delete all location records asynchronously.
     *
     * @param callback Optional. Receives the number of deleted rows. May be null.
     */
    public void deleteAll(@Nullable CountCallback callback) {
        writeExecutor.execute(() -> {
            int deleted = dao.deleteAll();
            if (callback != null) {
                callback.onResult(deleted);
            }
        });
    }

    /**
     * Convenience overload — clear all history without a result callback.
     */
    public void deleteAll() {
        deleteAll(null);
    }

    // ─── Read Operations ─────────────────────────────────────────────────────

    /**
     * Observe the complete location history, newest first.
     *
     * Returns LiveData — Room re-emits whenever the table changes.
     * The ViewModel exposes this directly; Activities observe it
     * and the RecyclerView updates automatically.
     *
     * Room handles the background threading for LiveData queries internally.
     * Safe to call and observe from the main thread.
     *
     * @return LiveData wrapping the full location list, ordered newest-first.
     */
    public LiveData<List<LocationEntity>> getAllLocations() {
        return dao.getAllLocations();
    }

    /**
     * Fetch the single most recent location record asynchronously.
     *
     * Dispatched to the background executor. Result delivered via callback
     * on the background thread — post to main thread if UI update needed.
     *
     * @param callback Receives the latest LocationEntity, or null if table is empty.
     */
    public void getLatestLocation(@NonNull LocationCallback callback) {
        writeExecutor.execute(() -> {
            LocationEntity latest = dao.getLatestLocation();
            callback.onResult(latest);
        });
    }

    /**
     * Get the total count of stored location records asynchronously.
     *
     * @param callback Receives the row count.
     */
    public void getLocationCount(@NonNull CountCallback callback) {
        writeExecutor.execute(() -> {
            int count = dao.getLocationCount();
            callback.onResult(count);
        });
    }

    /**
     * Observe locations within a time range.
     * Room handles threading for LiveData queries.
     *
     * @param startTime Start of range, UTC epoch ms (inclusive)
     * @param endTime   End of range, UTC epoch ms (inclusive)
     * @return LiveData list of matching records, newest first.
     */
    public LiveData<List<LocationEntity>> getLocationsBetween(long startTime, long endTime) {
        return dao.getLocationsBetween(startTime, endTime);
    }

    // ─── Executor Lifecycle ──────────────────────────────────────────────────

    /**
     * Shuts down the background executor gracefully.
     *
     * Call this only when the repository is truly no longer needed —
     * in practice, never for a singleton. Provided for completeness
     * and instrumented test teardown.
     *
     * After calling this, no further write operations will execute.
     */
    public void shutdown() {
        if (!writeExecutor.isShutdown()) {
            writeExecutor.shutdown();
        }
    }
}
