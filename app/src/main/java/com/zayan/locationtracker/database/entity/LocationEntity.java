package com.zayan.locationtracker.database.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "locations")
public class LocationEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private long id;

    @ColumnInfo(name = "latitude")
    private double latitude;

    @ColumnInfo(name = "longitude")
    private double longitude;

    @ColumnInfo(name = "accuracy")
    private float accuracy;

    @ColumnInfo(name = "provider")
    private String provider;

    @ColumnInfo(name = "timestamp")
    private long timestamp;

    public LocationEntity() {}

    public LocationEntity(double latitude, double longitude, float accuracy, String provider, long timestamp) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
        this.provider = provider;
        this.timestamp = timestamp;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public float getAccuracy() { return accuracy; }
    public void setAccuracy(float accuracy) { this.accuracy = accuracy; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
