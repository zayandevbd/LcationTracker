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

public class LocationRepository {

    private static volatile LocationRepository instance;

    private final LocationDao dao;
    private final ExecutorService writeExecutor;

    private LocationRepository(@NonNull Context context) {
        AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
        this.dao = db.locationDao();
        this.writeExecutor = Executors.newSingleThreadExecutor();
    }

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

    public interface InsertCallback {
        void onInserted(long rowId);
    }

    public interface LocationCallback {
        void onResult(@Nullable LocationEntity location);
    }

    public interface CountCallback {
        void onResult(int count);
    }

    public void insert(@NonNull LocationEntity location, @Nullable InsertCallback callback) {
        writeExecutor.execute(() -> {
            long rowId = dao.insert(location);
            if (callback != null) {
                callback.onInserted(rowId);
            }
        });
    }

    public void insert(@NonNull LocationEntity location) {
        insert(location, null);
    }

    public void delete(@NonNull LocationEntity location) {
        writeExecutor.execute(() -> dao.delete(location));
    }

    public void deleteAll(@Nullable CountCallback callback) {
        writeExecutor.execute(() -> {
            int deleted = dao.deleteAll();
            if (callback != null) {
                callback.onResult(deleted);
            }
        });
    }

    public void deleteAll() {
        deleteAll(null);
    }

    public LiveData<List<LocationEntity>> getAllLocations() {
        return dao.getAllLocations();
    }

    public void getLatestLocation(@NonNull LocationCallback callback) {
        writeExecutor.execute(() -> {
            LocationEntity latest = dao.getLatestLocation();
            callback.onResult(latest);
        });
    }

    public void getLocationCount(@NonNull CountCallback callback) {
        writeExecutor.execute(() -> {
            int count = dao.getLocationCount();
            callback.onResult(count);
        });
    }

    public LiveData<List<LocationEntity>> getLocationsBetween(long startTime, long endTime) {
        return dao.getLocationsBetween(startTime, endTime);
    }

    public void shutdown() {
        if (!writeExecutor.isShutdown()) {
            writeExecutor.shutdown();
        }
    }
}
