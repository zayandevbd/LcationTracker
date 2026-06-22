package com.zayan.locationtracker.database.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity representing a single recorded location fix.
 *
 * Maps to the "locations" table in the local SQLite database.
 * Each row corresponds to one successful location update received
 * from FusedLocationProviderClient.
 *
 * Design note: column names are explicitly declared via @ColumnInfo so
 * that Java field renames during refactoring don't silently change the
 * database schema and break existing stored data.
 */
@Entity(tableName = "locations")
public class LocationEntity {

    /**
     * Auto-generated primary key. Room/SQLite handles assignment.
     * We never set this manually — always leave it as 0 when inserting
     * and Room will replace it with the actual row ID.
     */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private long id;

    /** Decimal degrees, WGS84 coordinate system. Range: -90.0 to 90.0 */
    @ColumnInfo(name = "latitude")
    private double latitude;

    /** Decimal degrees, WGS84 coordinate system. Range: -180.0 to 180.0 */
    @ColumnInfo(name = "longitude")
    private double longitude;

    /**
     * Estimated horizontal accuracy in metres (1-sigma radius).
     * A value of 5.0 means the true location is within 5m ~68% of the time.
     * Negative values indicate accuracy is not available from this provider.
     */
    @ColumnInfo(name = "accuracy")
    private float accuracy;

    /**
     * Location provider that produced this fix.
     * Typical values: "gps", "network", "fused", "passive".
     * Stored as a string — provider set can change across Android versions
     * and OEM implementations, so an enum would be too rigid.
     */
    @ColumnInfo(name = "provider")
    private String provider;

    /**
     * UTC timestamp in milliseconds since epoch (System.currentTimeMillis()).
     * Stored as a long — timezone-independent, directly sortable,
     * and compatible with java.util.Date / DateFormat for display.
     */
    @ColumnInfo(name = "timestamp")
    private long timestamp;

    // ─── Constructors ────────────────────────────────────────────────────────

    /**
     * No-arg constructor required by Room's reflection-based instantiation.
     * Not intended for use in application code — use the parameterised
     * constructor below to ensure all fields are populated.
     */
    public LocationEntity() {
    }

    /**
     * Full constructor for creating a new location record before insertion.
     * Leave {@code id} as 0 — Room will auto-assign the real ID on insert.
     *
     * @param latitude  Latitude in decimal degrees
     * @param longitude Longitude in decimal degrees
     * @param accuracy  Horizontal accuracy in metres, or negative if unavailable
     * @param provider  Name of the location provider (e.g. "fused", "gps")
     * @param timestamp UTC epoch milliseconds when the fix was obtained
     */
    public LocationEntity(double latitude, double longitude,
                          float accuracy, String provider, long timestamp) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
        this.provider = provider;
        this.timestamp = timestamp;
    }

    // ─── Getters & Setters ───────────────────────────────────────────────────
    // Room needs public getters/setters (or public fields) to read/write data.
    // We use getters/setters to maintain encapsulation.

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public float getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(float accuracy) {
        this.accuracy = accuracy;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    // ─── Utility ─────────────────────────────────────────────────────────────

    /**
     * Human-readable representation — useful for logging and debugging.
     * Never expose this in production UI directly; format timestamps
     * properly using DateFormat in the adapter layer.
     */
    @Override
    public String toString() {
        return "LocationEntity{" +
                "id=" + id +
                ", lat=" + latitude +
                ", lon=" + longitude +
                ", accuracy=" + accuracy + "m" +
                ", provider='" + provider + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
