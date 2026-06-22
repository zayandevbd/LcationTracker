package com.zayan.locationtracker.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.zayan.locationtracker.database.entity.LocationEntity;

import java.util.List;

@Dao
public interface LocationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(LocationEntity location);

    @Delete
    void delete(LocationEntity location);

    @Query("DELETE FROM locations")
    int deleteAll();

    @Query("SELECT * FROM locations ORDER BY timestamp DESC")
    LiveData<List<LocationEntity>> getAllLocations();

    @Query("SELECT * FROM locations ORDER BY timestamp DESC LIMIT 1")
    LocationEntity getLatestLocation();

    @Query("SELECT COUNT(*) FROM locations")
    int getLocationCount();

    @Query("SELECT * FROM locations WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    LiveData<List<LocationEntity>> getLocationsBetween(long startTime, long endTime);
}
