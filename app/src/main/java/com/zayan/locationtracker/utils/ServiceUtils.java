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

/**
 * Utility methods for service lifecycle management and system-level checks.
 *
 * All methods are static — this class holds no state and should never
 * be instantiated. It exists to keep service-related boilerplate out
 * of Activity and ViewModel code.
 */
public final class ServiceUtils {

    private static final String TAG = Constants.LOG_TAG + "/ServiceUtils";

    private ServiceUtils() {
        throw new AssertionError("ServiceUtils is a utility class — do not instantiate.");
    }

    // ─── Service Start / Stop ────────────────────────────────────────────────

    /**
     * Start location tracking by launching the foreground service.
     *
     * Uses ContextCompat.startForegroundService() which calls the correct
     * API for the running Android version:
     *  - API 26+: Context.startForegroundService() — the service MUST call
     *             startForeground() within 5 seconds or ANR/crash occurs.
     *  - API < 26: Context.startService() — standard service start.
     *
     * Duplicate start protection: checks if the service is already running
     * before sending the start intent. Prevents stacking multiple instances.
     *
     * @param context Any context — service is started in the app process.
     */
    public static void startTracking(@NonNull Context context) {
        if (isServiceRunning(context)) {
            Log.d(TAG, "startTracking: service already running, ignoring duplicate start.");
            return;
        }

        Intent intent = new Intent(context, LocationTrackingService.class);
        intent.setAction(Constants.ACTION_START_TRACKING);

        ContextCompat.startForegroundService(context, intent);
        Log.d(TAG, "startTracking: service start intent sent.");
    }

    /**
     * Stop location tracking by sending a stop action to the running service.
     *
     * Sends an explicit intent with ACTION_STOP_TRACKING — the service
     * handles this in onStartCommand() and calls stopForeground() + stopSelf().
     * We don't call stopService() directly because the service needs to clean
     * up its location callbacks gracefully before stopping.
     *
     * @param context Any context.
     */
    public static void stopTracking(@NonNull Context context) {
        if (!isServiceRunning(context)) {
            Log.d(TAG, "stopTracking: service not running, nothing to stop.");
            return;
        }

        Intent intent = new Intent(context, LocationTrackingService.class);
        intent.setAction(Constants.ACTION_STOP_TRACKING);
        context.startService(intent);
        Log.d(TAG, "stopTracking: service stop intent sent.");
    }

    /**
     * Send a new interval to the running service without restarting it.
     *
     * The service receives this in onStartCommand() and reconfigures
     * FusedLocationProviderClient in-place — no location callback gap.
     * If the service isn't running, this is a no-op (the interval will
     * be read from SharedPreferences when the service next starts).
     *
     * @param context    Any context.
     * @param intervalMs New update interval in milliseconds.
     */
    public static void updateInterval(@NonNull Context context, long intervalMs) {
        if (!isServiceRunning(context)) {
            Log.d(TAG, "updateInterval: service not running, interval saved to prefs only.");
            return;
        }

        Intent intent = new Intent(context, LocationTrackingService.class);
        intent.setAction(Constants.ACTION_UPDATE_INTERVAL);
        intent.putExtra(Constants.EXTRA_NEW_INTERVAL_MS, intervalMs);
        context.startService(intent);
        Log.d(TAG, "updateInterval: sent new interval " + intervalMs + "ms to service.");
    }

    // ─── Service State Check ─────────────────────────────────────────────────

    /**
     * Check whether LocationTrackingService is currently running.
     *
     * Implementation note: ActivityManager.getRunningServices() is deprecated
     * for general third-party service detection since API 26, but it remains
     * fully functional for detecting services within your OWN application.
     * The deprecation targets apps trying to spy on other apps' services.
     * This is the standard and correct approach for self-service detection.
     *
     * @param context Any context.
     * @return true if the service is listed as a running service.
     */
    @SuppressWarnings("deprecation")
    public static boolean isServiceRunning(@NonNull Context context) {
        ActivityManager manager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        if (manager == null) {
            Log.w(TAG, "isServiceRunning: ActivityManager is null — assuming not running.");
            return false;
        }

        // getRunningServices(int maxNum) — we check up to 100 services.
        // Our service will always appear in this list if it's running.
        List<ActivityManager.RunningServiceInfo> services =
                manager.getRunningServices(100);

        if (services == null) {
            return false;
        }

        String targetService = LocationTrackingService.class.getName();
        for (ActivityManager.RunningServiceInfo info : services) {
            if (targetService.equals(info.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    // ─── Battery Optimization ────────────────────────────────────────────────

    /**
     * Check whether this app is excluded from battery optimization (Doze mode).
     *
     * Apps not on the battery whitelist may have background services throttled
     * or killed by Doze, especially on OEM devices with aggressive power management
     * (MIUI, ColorOS, OneUI). Being whitelisted ensures the foreground service
     * runs reliably.
     *
     * PowerManager.isIgnoringBatteryOptimizations() is available from API 23.
     * Our minSdk is 24, so no version guard is needed.
     *
     * @param context Any context.
     * @return true if the app is already on the battery whitelist.
     */
    public static boolean isIgnoringBatteryOptimizations(@NonNull Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm == null) {
            Log.w(TAG, "isIgnoringBatteryOptimizations: PowerManager null — assuming false.");
            return false;
        }
        return pm.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    /**
     * Build an intent to open the system battery optimization settings for this app.
     *
     * Shows the "Battery optimization" detail screen where the user can tap
     * "Don't optimize" to whitelist the app. This is the correct flow — we
     * cannot whitelist ourselves programmatically without a system permission
     * that Google Play disallows. We can only guide the user there.
     *
     * Note: Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS can be used
     * to show a system dialog directly, but Google Play policies restrict its
     * use to apps with a genuine need (navigation, health monitoring, etc.).
     * Opening the settings page instead is always safe.
     *
     * @param context Any context.
     * @return Intent that opens the battery optimization settings for this app.
     */
    @NonNull
    public static Intent getBatteryOptimizationSettingsIntent(@NonNull Context context) {
        Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        // Some OEMs support the direct app-specific screen via this URI.
        // Falls back to the general battery optimization list if not supported.
        try {
            Intent directIntent = new Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + context.getPackageName())
            );
            // Test if this intent can be resolved before returning it.
            if (directIntent.resolveActivity(context.getPackageManager()) != null) {
                return directIntent;
            }
        } catch (Exception e) {
            Log.w(TAG, "Direct battery optimization intent not supported: " + e.getMessage());
        }
        return intent;
    }

    // ─── GPS State Check ─────────────────────────────────────────────────────

    /**
     * Check whether the device's GPS (location services) is currently enabled.
     *
     * Uses the Settings.Secure provider string rather than LocationManager
     * directly — more reliable across Android versions and doesn't require
     * the location permission to check.
     *
     * @param context Any context.
     * @return true if location services are enabled at the system level.
     */
    public static boolean isLocationEnabled(@NonNull Context context) {
        try {
            int locationMode = Settings.Secure.getInt(
                    context.getContentResolver(),
                    Settings.Secure.LOCATION_MODE
            );
            return locationMode != Settings.Secure.LOCATION_MODE_OFF;
        } catch (Settings.SettingNotFoundException e) {
            Log.w(TAG, "isLocationEnabled: LOCATION_MODE setting not found: " + e.getMessage());
            // If we can't determine the state, assume enabled and let the
            // location client report the error naturally.
            return true;
        }
    }

    /**
     * Build an intent to open the system Location Settings screen.
     * Use this when GPS is disabled and we need to direct the user to enable it.
     *
     * @return Intent that opens Android's Location Settings page.
     */
    @NonNull
    public static Intent getLocationSettingsIntent() {
        return new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
    }
}
