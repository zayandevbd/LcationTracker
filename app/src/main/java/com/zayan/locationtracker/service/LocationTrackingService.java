package com.zayan.locationtracker.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.zayan.locationtracker.database.entity.LocationEntity;
import com.zayan.locationtracker.location.LocationHelper;
import com.zayan.locationtracker.notification.NotificationHelper;
import com.zayan.locationtracker.receiver.ServiceRestartReceiver;
import com.zayan.locationtracker.repository.LocationRepository;
import com.zayan.locationtracker.settings.AppSettings;
import com.zayan.locationtracker.utils.Constants;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Foreground service that continuously tracks device location.
 *
 * Lifecycle overview:
 *  - Started by MainActivity or BootReceiver via ServiceUtils.startTracking()
 *  - Calls startForeground() immediately in onStartCommand() — required on API 26+
 *  - Requests location updates via LocationHelper
 *  - Persists each fix to Room via LocationRepository
 *  - Shows a Toast and broadcasts each update to the UI
 *  - Handles ACTION_STOP_TRACKING and ACTION_UPDATE_INTERVAL intents
 *  - Returns START_STICKY — OS will restart if killed under memory pressure
 *  - Schedules a restart via ServiceRestartReceiver in onTaskRemoved()
 *  - Cleans up location callbacks and releases WakeLock in onDestroy()
 *
 * Threading:
 *  - onStartCommand() runs on main thread
 *  - LocationCallback delivered on main looper (by LocationHelper)
 *  - Database writes dispatched to background executor by LocationRepository
 *  - Broadcasts sent on main thread via LocalBroadcastManager
 */
public class LocationTrackingService extends Service
        implements LocationHelper.LocationUpdateListener {

    private static final String TAG = Constants.LOG_TAG + "/Service";

    // ─── Dependencies ─────────────────────────────────────────────────────────

    private LocationHelper locationHelper;
    private NotificationHelper notificationHelper;
    private LocationRepository repository;
    private AppSettings appSettings;

    /** Handler on the main looper — used to post Toasts safely from any thread. */
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** Date formatter for Toast and notification display. */
    private final SimpleDateFormat timeFormatter =
            new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    /** Prevents "GPS unavailable" toast from spamming if availability flickers. */
    private boolean gpsUnavailableToastShown = false;

    // ─── Service Lifecycle ────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        // Initialise dependencies — doing this in onCreate() means they're ready
        // before onStartCommand() is called, regardless of how many times
        // the service is started/restarted.
        appSettings = AppSettings.getInstance(this);
        repository = LocationRepository.getInstance(this);
        notificationHelper = new NotificationHelper(this);
        locationHelper = new LocationHelper(this, this);

        // Create notification channel early — required before startForeground().
        // Safe to call repeatedly; Android ignores duplicate channel creation.
        notificationHelper.createNotificationChannel();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: action=" +
                (intent != null ? intent.getAction() : "null (restarted by OS)"));

        // ── MUST call startForeground() within 5 seconds of onStartCommand ──
        // Do this first, before any other work, on every onStartCommand call.
        // On API 26+ the system throws if this isn't called in time.
        // On subsequent calls (interval update, stop) calling startForeground()
        // again is harmless — it just updates the notification.
        startForeground(Constants.NOTIFICATION_ID, notificationHelper.buildTrackingNotification());

        // Handle null intent — this means the OS restarted us via START_STICKY
        // after an OOM kill. Resume tracking with the saved interval.
        if (intent == null || Constants.ACTION_START_TRACKING.equals(intent.getAction())) {
            handleStartTracking();
        } else if (Constants.ACTION_STOP_TRACKING.equals(intent.getAction())) {
            handleStopTracking();
        } else if (Constants.ACTION_UPDATE_INTERVAL.equals(intent.getAction())) {
            long newInterval = intent.getLongExtra(
                    Constants.EXTRA_NEW_INTERVAL_MS, Constants.DEFAULT_INTERVAL_MS);
            handleUpdateInterval(newInterval);
        }

        // START_STICKY: if killed by the system, restart with a null intent.
        // We handle null intent above by resuming tracking at the saved interval.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");

        // Stop location updates — critical to prevent callback firing into dead service.
        if (locationHelper != null) {
            locationHelper.stopUpdates();
        }

        // Stop foreground and remove the notification.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            //noinspection deprecation
            stopForeground(true);
        }

        // Persist tracking state as false — used by BootReceiver after reboot.
        // Uses commit() (synchronous) — process may die immediately after onDestroy().
        appSettings.setWasTracking(false);

        // Broadcast to UI that tracking has stopped.
        broadcastTrackingStatus(false);

        super.onDestroy();
    }

    /**
     * Called when the user swipes the app away from the Recents screen.
     * The service is not destroyed here (stopWithTask="false" in manifest),
     * but we schedule a restart via AlarmManager as insurance against
     * aggressive OEM task killers that sometimes kill services on task removal.
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.d(TAG, "onTaskRemoved: scheduling service restart.");
        scheduleServiceRestart();
    }

    /** This service is not designed for binding. Returns null. */
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ─── Action Handlers ─────────────────────────────────────────────────────

    private void handleStartTracking() {
        if (locationHelper.isRequestingUpdates()) {
            Log.d(TAG, "handleStartTracking: already tracking — ignoring.");
            return;
        }

        gpsUnavailableToastShown = false; // reset for new tracking session
        long intervalMs = appSettings.getIntervalMs();
        locationHelper.startUpdates(intervalMs);

        // Persist that tracking is active — BootReceiver reads this after reboot.
        appSettings.setWasTracking(true);

        // Notify UI.
        broadcastTrackingStatus(true);

        Log.d(TAG, "handleStartTracking: tracking started at " + intervalMs + "ms interval.");
    }

    private void handleStopTracking() {
        locationHelper.stopUpdates();
        appSettings.setWasTracking(false);
        broadcastTrackingStatus(false);

        // Stop the service cleanly.
        stopSelf();

        Log.d(TAG, "handleStopTracking: service stopping.");
    }

    private void handleUpdateInterval(long newIntervalMs) {
        Log.d(TAG, "handleUpdateInterval: " + newIntervalMs + "ms");
        appSettings.setIntervalMs(newIntervalMs);
        locationHelper.updateInterval(newIntervalMs);

        // Update notification to reflect new interval.
        notificationHelper.updateNotification(notificationHelper.buildTrackingNotification());
    }

    // ─── LocationHelper.LocationUpdateListener ────────────────────────────────

    /**
     * Called by LocationHelper on the main thread when a new location fix arrives.
     *
     * Order of operations:
     *  1. Build the entity
     *  2. Persist to Room (background thread via repository)
     *  3. Show Toast on main thread
     *  4. Update the foreground notification
     *  5. Broadcast to any listening UI components
     */
    @Override
    public void onLocationUpdated(double latitude, double longitude,
                                  float accuracy, String provider, long timestamp) {
        Log.d(TAG, String.format("onLocationUpdated: %.6f, %.6f ±%.1fm via %s",
                latitude, longitude, accuracy, provider));

        // 1. Build entity for persistence.
        LocationEntity entity = new LocationEntity(
                latitude, longitude, accuracy, provider, timestamp);

        // 2. Persist asynchronously — repository handles the background thread.
        repository.insert(entity);

        // 3. Show Toast — already on main thread (LocationCallback on main looper).
        String timeStr = timeFormatter.format(new Date(timestamp));
        showLocationToast(latitude, longitude, timeStr);

        // 4. Update the persistent notification with latest coords.
        notificationHelper.updateNotification(
                notificationHelper.buildLocationUpdateNotification(latitude, longitude, timeStr)
        );

        // 5. Broadcast to UI (MainActivity/HistoryActivity if open).
        broadcastLocationUpdate(latitude, longitude, accuracy, provider, timestamp);
    }

    /**
     * Called by LocationHelper when GPS availability changes.
     * Only shows the "unavailable" toast once per outage — not on every
     * repeated callback. Resets when GPS becomes available again.
     */
    @Override
    public void onLocationAvailabilityChanged(boolean available) {
        Log.d(TAG, "onLocationAvailabilityChanged: " + available);
        if (!available && !gpsUnavailableToastShown) {
            gpsUnavailableToastShown = true;
            mainHandler.post(() ->
                    Toast.makeText(getApplicationContext(),
                            "GPS signal lost — waiting to reconnect...",
                            Toast.LENGTH_SHORT).show()
            );
        } else if (available) {
            // GPS recovered — reset so we can warn again if it drops again later.
            gpsUnavailableToastShown = false;
        }
    }

    // ─── Toast ────────────────────────────────────────────────────────────────

    /**
     * Show the location update Toast.
     *
     * LocationCallback is delivered on the main looper, so we're already
     * on the main thread here. The mainHandler.post() wraps it defensively
     * in case this is ever called from a background path in the future.
     */
    private void showLocationToast(double latitude, double longitude, String timeStr) {
        String message = String.format(Locale.getDefault(),
                "Latitude: %.6f\nLongitude: %.6f\nTime: %s",
                latitude, longitude, timeStr);

        mainHandler.post(() ->
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show()
        );
    }

    // ─── Broadcasts ───────────────────────────────────────────────────────────

    /**
     * Broadcast a new location fix to any registered UI receivers.
     * Uses LocalBroadcastManager — app-internal only, not system-wide.
     */
    private void broadcastLocationUpdate(double latitude, double longitude,
                                         float accuracy, String provider, long timestamp) {
        Intent intent = new Intent(Constants.ACTION_LOCATION_UPDATE);
        intent.putExtra(Constants.EXTRA_LATITUDE, latitude);
        intent.putExtra(Constants.EXTRA_LONGITUDE, longitude);
        intent.putExtra(Constants.EXTRA_ACCURACY, accuracy);
        intent.putExtra(Constants.EXTRA_PROVIDER, provider);
        intent.putExtra(Constants.EXTRA_TIMESTAMP, timestamp);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    /**
     * Broadcast the current tracking state to UI components.
     *
     * @param isTracking true if service just started, false if stopping.
     */
    private void broadcastTrackingStatus(boolean isTracking) {
        Intent intent = new Intent(Constants.ACTION_TRACKING_STATUS_CHANGED);
        intent.putExtra(Constants.EXTRA_IS_TRACKING, isTracking);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    // ─── Service Restart ──────────────────────────────────────────────────────

    /**
     * Schedule a service restart via AlarmManager.
     *
     * Called from onTaskRemoved() to handle aggressive OEM task killers
     * (MIUI, ColorOS, etc.) that kill services when the app is removed
     * from Recents, despite stopWithTask="false" in the manifest.
     *
     * Uses ELAPSED_REALTIME (not RTC) — fires relative to boot time,
     * not wall clock. More reliable for short delays.
     *
     * On API 31+ setExact() requires SCHEDULE_EXACT_ALARM permission
     * or the user having granted exact alarm scheduling.
     * We use setAndAllowWhileIdle() as a safer fallback for this use case.
     */
    private void scheduleServiceRestart() {
        try {
            Intent restartIntent = new Intent(this, ServiceRestartReceiver.class);
            restartIntent.setAction(Constants.ACTION_RESTART_SERVICE);

            int flags = PendingIntent.FLAG_ONE_SHOT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    this, 1, restartIntent, flags);

            AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (alarmManager != null) {
                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + Constants.SERVICE_RESTART_DELAY_MS,
                        pendingIntent
                );
                Log.d(TAG, "scheduleServiceRestart: restart scheduled in "
                        + Constants.SERVICE_RESTART_DELAY_MS + "ms.");
            }
        } catch (Exception e) {
            Log.e(TAG, "scheduleServiceRestart: failed — " + e.getMessage());
        }
    }

    // ─── LocalBroadcastManager dependency ────────────────────────────────────
    // Note: LocalBroadcastManager is in the androidx.localbroadcastmanager artifact.
    // If the import shows unresolved, add to build.gradle.kts:
    // implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    // We handle this in the next build.gradle update pass.
}
