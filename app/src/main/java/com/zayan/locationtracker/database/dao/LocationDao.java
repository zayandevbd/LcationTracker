package com.zayan.locationtracker.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.zayan.locationtracker.database.entity.LocationEntity;

import java.util.List;

/**
 * Data Access Object for the locations table.
 *
 * All database operations for location records go through here.
 * Room generates the implementation at compile time — if a query
 * has a syntax error or references a non-existent column, the build
 * fails rather than the app crashing at runtime. That's the point.
 *
 * Threading rules:
 *  - Methods returning LiveData are safe to call on the main thread —
 *    Room executes the query on a background thread automatically.
 *  - All other methods MUST be called from a background thread.
 *    The Repository enforces this via Executors.
 */
@Dao
public interface LocationDao {

    /**
     * Insert a new location record into the database.
     *
     * Uses IGNORE conflict strategy — if somehow a duplicate primary key
     * is inserted (shouldn't happen with autoGenerate, but defensive),
     * the new row is silently dropped rather than crashing.
     *
     * @param location The location entity to persist. The id field should
     *                 be 0 — Room will assign the actual row ID.
     * @return The SQLite row ID of the newly inserted record,
     *         or -1 if the insert was ignored due to a conflict.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(LocationEntity location);

    /**
     * Delete a specific location record by its entity reference.
     * Room uses the primary key (id) field to identify the row.
     *
     * @param location The entity to delete. Must have a valid id.
     */
    @Delete
    void delete(LocationEntity location);

    /**
     * Wipe the entire location history.
     * Returns the number of rows deleted — useful for confirming
     * the operation actually ran against a non-empty table.
     *
     * @return Number of rows removed from the locations table.
     */
    @Query("DELETE FROM locations")
    int deleteAll();

    /**
     * Fetch the complete location history, newest entries first.
     *
     * Returns LiveData — Room observes the table and re-emits the
     * updated list automatically whenever any insert or delete occurs.
     * The History screen's RecyclerView stays in sync without any
     * manual refresh calls.
     *
     * The ORDER BY is enforced here at the SQL level, not sorted
     * in Java after fetching — more efficient, especially as the
     * table grows large.
     *
     * @return LiveData wrapping the full list ordered newest-first.
     */
    @Query("SELECT * FROM locations ORDER BY timestamp DESC")
    LiveData<List<LocationEntity>> getAllLocations();

    /**
     * Fetch the single most recent location record.
     *
     * Used by the service and main screen to display current status
     * without needing to observe the full history list.
     * Returns null if the table is empty — callers must handle that.
     *
     * This is a one-shot synchronous query (no LiveData wrapper),
     * so it MUST be called from a background thread.
     *
     * @return The most recently inserted LocationEntity, or null.
     */
    @Query("SELECT * FROM locations ORDER BY timestamp DESC LIMIT 1")
    LocationEntity getLatestLocation();

    /**
     * Get the total count of stored location records.
     * Useful for displaying record count in the UI or deciding
     * whether to show an empty state view.
     *
     * @return Total number of rows in the locations table.
     */
    @Query("SELECT COUNT(*) FROM locations")
    int getLocationCount();

    /**
     * Fetch locations recorded within a specific time range.
     * Not used in the initial UI but provided for future filtering
     * features (e.g. "show today's tracking" or export by date range).
     *
     * @param startTime Start of range, UTC epoch milliseconds (inclusive)
     * @param endTime   End of range, UTC epoch milliseconds (inclusive)
     * @return LiveData list of matching records, newest first.
     */
    @Query("SELECT * FROM locations WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    LiveData<List<LocationEntity>> getLocationsBetween(long startTime, long endTime);
}
