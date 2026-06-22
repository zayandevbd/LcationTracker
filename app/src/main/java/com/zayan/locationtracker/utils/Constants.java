package com.zayan.locationtracker.utils;

public final class Constants {

    private Constants() {
        throw new AssertionError("Constants is a utility class — do not instantiate.");
    }

    // Interval presets in milliseconds
    public static final long INTERVAL_5_MIN_MS  = 5  * 60 * 1000L;
    public static final long INTERVAL_10_MIN_MS = 10 * 60 * 1000L;
    public static final long INTERVAL_15_MIN_MS = 15 * 60 * 1000L;
    public static final long INTERVAL_30_MIN_MS = 30 * 60 * 1000L;

    public static final long DEFAULT_INTERVAL_MS = INTERVAL_5_MIN_MS;

    public static final long[] INTERVAL_OPTIONS_MS = {
            INTERVAL_5_MIN_MS,
            INTERVAL_10_MIN_MS,
            INTERVAL_15_MIN_MS,
            INTERVAL_30_MIN_MS
    };

    // Indices must match INTERVAL_OPTIONS_MS
    public static final String[] INTERVAL_LABELS = {
            "5 minutes",
            "10 minutes",
            "15 minutes",
            "30 minutes"
    };

    public static final long FASTEST_INTERVAL_MS = 2 * 60 * 1000L;
    public static final float MIN_DISPLACEMENT_METERS = 0f;

    public static final String PREFS_NAME = "location_tracker_prefs";
    public static final String PREF_KEY_INTERVAL_MS = "pref_interval_ms";
    // read by BootReceiver to decide whether to restart after reboot
    public static final String PREF_KEY_WAS_TRACKING = "pref_was_tracking";

    public static final String NOTIFICATION_CHANNEL_ID = "location_tracking_channel";
    public static final String NOTIFICATION_CHANNEL_NAME = "Location Tracking";
    public static final String NOTIFICATION_CHANNEL_DESC =
            "Shows while location tracking is active in the background";
    public static final int NOTIFICATION_ID = 1001;

    // Broadcast actions — fully qualified to avoid collisions
    public static final String ACTION_LOCATION_UPDATE =
            "com.zayan.locationtracker.ACTION_LOCATION_UPDATE";
    public static final String ACTION_TRACKING_STATUS_CHANGED =
            "com.zayan.locationtracker.ACTION_TRACKING_STATUS_CHANGED";
    public static final String ACTION_RESTART_SERVICE =
            "com.zayan.locationtracker.ACTION_RESTART_SERVICE";

    // Intent extras
    public static final String EXTRA_LATITUDE = "extra_latitude";
    public static final String EXTRA_LONGITUDE = "extra_longitude";
    public static final String EXTRA_TIMESTAMP = "extra_timestamp";
    public static final String EXTRA_ACCURACY = "extra_accuracy";
    public static final String EXTRA_PROVIDER = "extra_provider";
    public static final String EXTRA_IS_TRACKING = "extra_is_tracking";
    public static final String EXTRA_NEW_INTERVAL_MS = "extra_new_interval_ms";

    // Service intent actions
    public static final String ACTION_START_TRACKING =
            "com.zayan.locationtracker.ACTION_START_TRACKING";
    public static final String ACTION_STOP_TRACKING =
            "com.zayan.locationtracker.ACTION_STOP_TRACKING";
    public static final String ACTION_UPDATE_INTERVAL =
            "com.zayan.locationtracker.ACTION_UPDATE_INTERVAL";

    public static final String LOG_TAG = "LocationTracker";

    public static final long SERVICE_RESTART_DELAY_MS = 1000L;
    public static final int DEFAULT_INTERVAL_INDEX = 0;
}
