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

public class LocationTrackingService extends Service
        implements LocationHelper.LocationUpdateListener {

    private static final String TAG = Constants.LOG_TAG + "/Service";

    private LocationHelper locationHelper;
    private NotificationHelper notificationHelper;
    private LocationRepository repository;
    private AppSettings appSettings;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFormatter =
            new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    private boolean gpsUnavailableToastShown = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        appSettings = AppSettings.getInstance(this);
        repository = LocationRepository.getInstance(this);
        notificationHelper = new NotificationHelper(this);
        locationHelper = new LocationHelper(this, this);
        notificationHelper.createNotificationChannel();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: action=" +
                (intent != null ? intent.getAction() : "null (restarted by OS)"));

        startForeground(Constants.NOTIFICATION_ID, notificationHelper.buildTrackingNotification());

        if (intent == null || Constants.ACTION_START_TRACKING.equals(intent.getAction())) {
            handleStartTracking();
        } else if (Constants.ACTION_STOP_TRACKING.equals(intent.getAction())) {
            handleStopTracking();
        } else if (Constants.ACTION_UPDATE_INTERVAL.equals(intent.getAction())) {
            long newInterval = intent.getLongExtra(
                    Constants.EXTRA_NEW_INTERVAL_MS, Constants.DEFAULT_INTERVAL_MS);
            handleUpdateInterval(newInterval);
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        if (locationHelper != null) {
            locationHelper.stopUpdates();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        appSettings.setWasTracking(false);
        broadcastTrackingStatus(false);
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.d(TAG, "onTaskRemoved: scheduling restart.");
        scheduleServiceRestart();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void handleStartTracking() {
        if (locationHelper.isRequestingUpdates()) {
            Log.d(TAG, "handleStartTracking: already tracking — ignoring.");
            return;
        }
        gpsUnavailableToastShown = false;
        long intervalMs = appSettings.getIntervalMs();
        locationHelper.startUpdates(intervalMs);
        appSettings.setWasTracking(true);
        broadcastTrackingStatus(true);
        Log.d(TAG, "handleStartTracking: started at " + intervalMs + "ms");
    }

    private void handleStopTracking() {
        locationHelper.stopUpdates();
        appSettings.setWasTracking(false);
        broadcastTrackingStatus(false);
        stopSelf();
        Log.d(TAG, "handleStopTracking: stopping.");
    }

    private void handleUpdateInterval(long newIntervalMs) {
        Log.d(TAG, "handleUpdateInterval: " + newIntervalMs + "ms");
        appSettings.setIntervalMs(newIntervalMs);
        locationHelper.updateInterval(newIntervalMs);
        notificationHelper.updateNotification(notificationHelper.buildTrackingNotification());
    }

    @Override
    public void onLocationUpdated(double latitude, double longitude,
                                  float accuracy, String provider, long timestamp) {
        Log.d(TAG, String.format("onLocationUpdated: %.6f, %.6f ±%.1fm via %s",
                latitude, longitude, accuracy, provider));

        repository.insert(new LocationEntity(latitude, longitude, accuracy, provider, timestamp));

        String timeStr = timeFormatter.format(new Date(timestamp));
        showLocationToast(latitude, longitude, timeStr);
        notificationHelper.updateNotification(
                notificationHelper.buildLocationUpdateNotification(latitude, longitude, timeStr));
        broadcastLocationUpdate(latitude, longitude, accuracy, provider, timestamp);
    }

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
            gpsUnavailableToastShown = false;
        }
    }

    private void showLocationToast(double latitude, double longitude, String timeStr) {
        String message = String.format(Locale.getDefault(),
                "Latitude: %.6f\nLongitude: %.6f\nTime: %s", latitude, longitude, timeStr);
        mainHandler.post(() ->
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show());
    }

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

    private void broadcastTrackingStatus(boolean isTracking) {
        Intent intent = new Intent(Constants.ACTION_TRACKING_STATUS_CHANGED);
        intent.putExtra(Constants.EXTRA_IS_TRACKING, isTracking);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

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
                Log.d(TAG, "scheduleServiceRestart: scheduled in " +
                        Constants.SERVICE_RESTART_DELAY_MS + "ms");
            }
        } catch (Exception e) {
            Log.e(TAG, "scheduleServiceRestart: failed — " + e.getMessage());
        }
    }
}
