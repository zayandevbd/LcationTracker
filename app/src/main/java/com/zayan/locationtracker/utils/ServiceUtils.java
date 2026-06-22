package com.zayan.locationtracker.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.zayan.locationtracker.service.LocationTrackingService;

import java.util.List;

public final class ServiceUtils {

    private static final String TAG = Constants.LOG_TAG + "/ServiceUtils";

    private ServiceUtils() {
        throw new AssertionError("ServiceUtils is a utility class — do not instantiate.");
    }

    public static void startTracking(@NonNull Context context) {
        if (isServiceRunning(context)) {
            Log.d(TAG, "startTracking: already running, ignoring.");
            return;
        }
        Intent intent = new Intent(context, LocationTrackingService.class);
        intent.setAction(Constants.ACTION_START_TRACKING);
        ContextCompat.startForegroundService(context, intent);
        Log.d(TAG, "startTracking: start intent sent.");
    }

    public static void stopTracking(@NonNull Context context) {
        if (!isServiceRunning(context)) {
            Log.d(TAG, "stopTracking: not running, nothing to stop.");
            return;
        }
        Intent intent = new Intent(context, LocationTrackingService.class);
        intent.setAction(Constants.ACTION_STOP_TRACKING);
        context.startService(intent);
        Log.d(TAG, "stopTracking: stop intent sent.");
    }

    // if service isn't running the saved interval will be picked up on next start
    public static void updateInterval(@NonNull Context context, long intervalMs) {
        if (!isServiceRunning(context)) {
            Log.d(TAG, "updateInterval: service not running, interval saved to prefs only.");
            return;
        }
        Intent intent = new Intent(context, LocationTrackingService.class);
        intent.setAction(Constants.ACTION_UPDATE_INTERVAL);
        intent.putExtra(Constants.EXTRA_NEW_INTERVAL_MS, intervalMs);
        context.startService(intent);
        Log.d(TAG, "updateInterval: sent " + intervalMs + "ms to service.");
    }

    @SuppressWarnings("deprecation")
    public static boolean isServiceRunning(@NonNull Context context) {
        ActivityManager manager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) {
            Log.w(TAG, "isServiceRunning: ActivityManager null — assuming not running.");
            return false;
        }
        List<ActivityManager.RunningServiceInfo> services = manager.getRunningServices(100);
        if (services == null) return false;

        String targetService = LocationTrackingService.class.getName();
        for (ActivityManager.RunningServiceInfo info : services) {
            if (targetService.equals(info.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isIgnoringBatteryOptimizations(@NonNull Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm == null) {
            Log.w(TAG, "isIgnoringBatteryOptimizations: PowerManager null — assuming false.");
            return false;
        }
        return pm.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    @NonNull
    public static Intent getBatteryOptimizationSettingsIntent(@NonNull Context context) {
        Intent fallback = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        try {
            Intent direct = new Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + context.getPackageName())
            );
            if (direct.resolveActivity(context.getPackageManager()) != null) {
                return direct;
            }
        } catch (Exception e) {
            Log.w(TAG, "Direct battery intent not supported: " + e.getMessage());
        }
        return fallback;
    }

    public static boolean isLocationEnabled(@NonNull Context context) {
        try {
            int mode = Settings.Secure.getInt(
                    context.getContentResolver(), Settings.Secure.LOCATION_MODE);
            return mode != Settings.Secure.LOCATION_MODE_OFF;
        } catch (Settings.SettingNotFoundException e) {
            Log.w(TAG, "isLocationEnabled: setting not found — assuming enabled.");
            return true;
        }
    }

    @NonNull
    public static Intent getLocationSettingsIntent() {
        return new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
    }
}
