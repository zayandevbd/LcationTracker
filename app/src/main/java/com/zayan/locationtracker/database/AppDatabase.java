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

@Database(
        entities = {LocationEntity.class},
        version = 1,
        exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "location_tracker.db";

    private static volatile AppDatabase instance;

    public abstract LocationDao locationDao();

    public static AppDatabase getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    DATABASE_NAME
                            )
                            .fallbackToDestructiveMigration()
                            .addCallback(creationCallback)
                            .build();
                }
            }
        }
        return instance;
    }

    private static final RoomDatabase.Callback creationCallback = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
            android.util.Log.d("AppDatabase", "Database created: " + DATABASE_NAME);
        }

        @Override
        public void onDestructiveMigration(@NonNull SupportSQLiteDatabase db) {
            super.onDestructiveMigration(db);
            android.util.Log.w("AppDatabase", "Destructive migration performed — all location history cleared.");
        }
    };

    @SuppressWarnings("unused")
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            throw new UnsupportedOperationException("Migration 1→2 not yet implemented.");
        }
    };

    @SuppressWarnings("unused")
    static void clearInstance() {
        if (instance != null && instance.isOpen()) {
            instance.close();
        }
        instance = null;
    }
}
