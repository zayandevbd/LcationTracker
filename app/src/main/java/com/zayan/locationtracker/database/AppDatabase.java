package com.zayan.locationtracker.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.zayan.locationtracker.database.dao.LocationDao;
import com.zayan.locationtracker.database.entity.LocationEntity;

/**
 * Room database singleton for the LocationTracker app.
 *
 * Manages the single SQLite connection used throughout the app.
 * Always access this via {@link #getInstance(Context)} — never
 * instantiate it directly or create multiple instances.
 *
 * Version history:
 *   1 — Initial schema: locations table with id, latitude, longitude,
 *       accuracy, provider, timestamp fields.
 *
 * Migration policy:
 *   Currently uses fallbackToDestructiveMigration() for simplicity
 *   since this is v1 with no existing user data to preserve.
 *   Before releasing any update that changes the schema, replace this
 *   with an explicit Migration object (see MIGRATION_1_2 stub below).
 */
@Database(
        entities = {LocationEntity.class},
        version = 1,
        exportSchema = true   // exports schema JSON to /app/schemas/ for version control
)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "location_tracker.db";

    /**
     * volatile ensures that writes to this field are immediately visible
     * to all threads. Without it, a second thread could see a stale null
     * and create a second database instance — violating the singleton contract.
     */
    private static volatile AppDatabase instance;

    // ─── DAO Access ──────────────────────────────────────────────────────────

    /**
     * Returns the DAO for location records.
     * Room generates the concrete implementation at compile time.
     */
    public abstract LocationDao locationDao();

    // ─── Singleton ───────────────────────────────────────────────────────────

    /**
     * Returns the singleton database instance, creating it if necessary.
     *
     * Uses double-checked locking to be thread-safe while avoiding
     * the overhead of synchronizing on every call after initialisation.
     *
     * Always pass {@code context.getApplicationContext()} — or let this
     * method handle it — to avoid leaking Activity or Service contexts
     * into a long-lived singleton.
     *
     * @param context Any context; application context is extracted internally.
     * @return The single shared AppDatabase instance.
     */
    public static AppDatabase getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                // Second check inside the lock — another thread may have
                // initialised the instance while we were waiting.
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    DATABASE_NAME
                            )
                            // For v1: if the schema ever changes without a written Migration,
                            // wipe and recreate instead of crashing with IllegalStateException.
                            // Replace this with addMigrations(MIGRATION_1_2) before any schema
                            // change in a released version that has real user data.
                            .fallbackToDestructiveMigration()
                            // Add a creation callback so we can log or seed data if needed.
                            .addCallback(creationCallback)
                            .build();
                }
            }
        }
        return instance;
    }

    // ─── Database Lifecycle Callbacks ────────────────────────────────────────

    /**
     * Callback fired when the database is created for the first time.
     * Also fires on destructive migration (schema wipe).
     * Useful for seeding initial data or logging in development.
     */
    private static final RoomDatabase.Callback creationCallback = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
            // Database created fresh. Could pre-populate data here if needed.
            // In production we log this event — helps diagnose unexpected wipes.
            android.util.Log.d("AppDatabase", "Database created: " + DATABASE_NAME);
        }

        @Override
        public void onDestructiveMigration(@NonNull SupportSQLiteDatabase db) {
            super.onDestructiveMigration(db);
            // This fires when fallbackToDestructiveMigration() is triggered.
            // Log it loudly — losing user data should always be visible in logs.
            android.util.Log.w("AppDatabase",
                    "Destructive migration performed — all location history cleared.");
        }
    };

    // ─── Migration Stubs ─────────────────────────────────────────────────────

    /**
     * Example migration stub for when a schema change is needed in a future release.
     *
     * Usage:
     *   .addMigrations(MIGRATION_1_2)
     *
     * Replace the body with the actual ALTER TABLE / CREATE TABLE statements
     * needed for that schema version bump.
     *
     * This stub is intentionally left here as documentation and a reminder
     * that fallbackToDestructiveMigration() must be replaced before v2.
     */
    @SuppressWarnings("unused")
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Example: adding a new column in version 2
            // database.execSQL("ALTER TABLE locations ADD COLUMN altitude REAL NOT NULL DEFAULT 0");
            throw new UnsupportedOperationException(
                    "Migration 1→2 not yet implemented. Write the SQL before releasing.");
        }
    };

    // ─── Test Support ────────────────────────────────────────────────────────

    /**
     * Clears the singleton instance — intended for use in unit tests only.
     * Instrumented tests should call this in @After to prevent database
     * state from leaking between test cases.
     *
     * Never call this in production code.
     */
    @SuppressWarnings("unused")
    static void clearInstance() {
        if (instance != null && instance.isOpen()) {
            instance.close();
        }
        instance = null;
    }
}
