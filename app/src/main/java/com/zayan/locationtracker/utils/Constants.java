package com.zayan.locationtracker.utils;

/**
 * Application-wide constants.
 *
 * All magic numbers, keys, action strings, and fixed configuration values
 * are defined here. No class in the app should hard-code any of these inline.
 *
 * Convention:
 *  - UPPER_SNAKE_CASE for all constants
 *  - Grouped by domain with section comments
 *  - Units included in the name where ambiguous (e.g. _MS for milliseconds)
 */
public final class Constants {

    // Prevent instantiation — this is a static constants holder only.
    private Constants() {
        throw new AssertionError("Constants is a utility class — do not instantiate.");
    }

    // ─── Location Update Intervals ────────────────────────────────────────────

    /** Five minutes in milliseconds — the default tracking interval */
    public static final long INTERVAL_5_MIN_MS  = 5  * 60 * 1000L;

    /** Ten minutes in milliseconds */
    public static final long INTERVAL_10_MIN_MS = 10 * 60 * 1000L;

    /** Fifteen minutes in milliseconds */
    public static final long INTERVAL_15_MIN_MS = 15 * 60 * 1000L;

    /** Thirty minutes in milliseconds */
    public static final long INTERVAL_30_MIN_MS = 30 * 60 * 1000L;

    /**
     * Default location update interval.
     * Used on first launch before the user changes the setting.
     */
    public static final long DEFAULT_INTERVAL_MS = INTERVAL_5_MIN_MS;

    /**
     * All available interval options, in ascending order.
     * The Settings spinner iterates this array for display labels.
     * Index in this array maps 1:1 with INTERVAL_LABELS below.
     */
    public static final long[] INTERVAL_OPTIONS_MS = {
            INTERVAL_5_MIN_MS,
            INTERVAL_10_MIN_MS,
            INTERVAL_15_MIN_MS,
            INTERVAL_30_MIN_MS
    };

    /**
     * Human-readable labels for each interval option.
     * Index must match INTERVAL_OPTIONS_MS exactly.
     * Displayed in the Settings screen spinner/radio buttons.
     */
    public static final String[] INTERVAL_LABELS = {
            "5 minutes",
            "10 minutes",
            "15 minutes",
            "30 minutes"
    };

    /**
     * Fastest interval cap — minimum time between location callbacks
     * even if another app is requesting high-frequency updates.
     */
    public static final long FASTEST_INTERVAL_MS = 2 * 60 * 1000L; // 2 minutes

    /**
     * Minimum displacement in metres before a location update is delivered.
     * Set to 0 — we want time-based updates regardless of movement.
     */
    public static final float MIN_DISPLACEMENT_METERS = 0f;

    // ─── SharedPreferences ────────────────────────────────────────────────────

    /** Name of the SharedPreferences file for app settings */
    public static final String PREFS_NAME = "location_tracker_prefs";

    /** Key for the stored location update interval (long, milliseconds) */
    public static final String PREF_KEY_INTERVAL_MS = "pref_interval_ms";

    /**
     * Key for whether tracking was active when the app last ran.
     * Used by BootReceiver to decide whether to restart the service after reboot.
     */
    public static final String PREF_KEY_WAS_TRACKING = "pref_was_tracking";

    // ─── Notification ─────────────────────────────────────────────────────────

    /**
     * Notification channel ID for the foreground service notification.
     * Must be consistent across all calls to NotificationChannel and
     * NotificationCompat.Builder — a mismatch silently breaks notifications.
     */
    public static final String NOTIFICATION_CHANNEL_ID = "location_tracking_channel";

    /** Human-readable name shown in system notification settings for this channel */
    public static final String NOTIFICATION_CHANNEL_NAME = "Location Tracking";

    /** Description shown in system notification settings for this channel */
    public static final String NOTIFICATION_CHANNEL_DESC =
            "Shows while location tracking is active in the background";

    /**
     * Notification ID for the foreground service persistent notification.
     * Must be > 0. Reusing this ID updates the existing notification
     * rather than stacking new ones.
     */
    public static final int NOTIFICATION_ID = 1001;

    // ─── Broadcast Actions ────────────────────────────────────────────────────
    // Fully qualified with package name to avoid collisions with other apps.

    /**
     * Broadcast sent by LocationTrackingService when a new location is received.
     * Extras: EXTRA_LATITUDE, EXTRA_LONGITUDE, EXTRA_TIMESTAMP
     */
    public static final String ACTION_LOCATION_UPDATE =
            "com.zayan.locationtracker.ACTION_LOCATION_UPDATE";

    /**
     * Broadcast sent by LocationTrackingService when it starts or stops.
     * Extras: EXTRA_IS_TRACKING
     */
    public static final String ACTION_TRACKING_STATUS_CHANGED =
            "com.zayan.locationtracker.ACTION_TRACKING_STATUS_CHANGED";

    /**
     * Internal broadcast action used by ServiceRestartReceiver to
     * re-launch the service after it has been killed by the system.
     */
    public static final String ACTION_RESTART_SERVICE =
            "com.zayan.locationtracker.ACTION_RESTART_SERVICE";

    // ─── Broadcast / Intent Extras ────────────────────────────────────────────

    /** Double — latitude of the latest location fix */
    public static final String EXTRA_LATITUDE = "extra_latitude";

    /** Double — longitude of the latest location fix */
    public static final String EXTRA_LONGITUDE = "extra_longitude";

    /** Long — UTC epoch milliseconds of the latest location fix */
    public static final String EXTRA_TIMESTAMP = "extra_timestamp";

    /** Float — horizontal accuracy in metres of the latest fix */
    public static final String EXTRA_ACCURACY = "extra_accuracy";

    /** String — provider name of the latest fix (e.g. "fused", "gps") */
    public static final String EXTRA_PROVIDER = "extra_provider";

    /** Boolean — whether tracking is currently active */
    public static final String EXTRA_IS_TRACKING = "extra_is_tracking";

    /** Long — new interval in milliseconds, sent when user changes setting */
    public static final String EXTRA_NEW_INTERVAL_MS = "extra_new_interval_ms";

    // ─── Service Intent Actions ───────────────────────────────────────────────

    /**
     * Action to start location tracking.
     * Pass as the intent action when starting LocationTrackingService.
     */
    public static final String ACTION_START_TRACKING =
            "com.zayan.locationtracker.ACTION_START_TRACKING";

    /**
     * Action to stop location tracking.
     * Pass as the intent action when stopping LocationTrackingService.
     */
    public static final String ACTION_STOP_TRACKING =
            "com.zayan.locationtracker.ACTION_STOP_TRACKING";

    /**
     * Action to update the location interval while the service is running.
     * Include the new interval via EXTRA_NEW_INTERVAL_MS.
     * The service reconfigures FusedLocationProviderClient without restarting.
     */
    public static final String ACTION_UPDATE_INTERVAL =
            "com.zayan.locationtracker.ACTION_UPDATE_INTERVAL";

    // ─── Logging ──────────────────────────────────────────────────────────────

    /** Log tag prefix — use per-class tags based on this for filtering */
    public static final String LOG_TAG = "LocationTracker";

    // ─── Miscellaneous ────────────────────────────────────────────────────────

    /**
     * Delay in milliseconds before the service restart receiver attempts
     * to re-launch the service after a system kill. A short delay gives
     * the system time to stabilise before the service comes back up.
     */
    public static final long SERVICE_RESTART_DELAY_MS = 1000L;

    /**
     * Default index into INTERVAL_OPTIONS_MS pointing to the 5-minute option.
     * Used to pre-select the correct spinner item on first launch.
     */
    public static final int DEFAULT_INTERVAL_INDEX = 0; // index of INTERVAL_5_MIN_MS
}
