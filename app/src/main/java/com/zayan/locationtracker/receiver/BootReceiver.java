package com.zayan.locationtracker.receiver;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.zayan.locationtracker.settings.AppSettings;
import com.zayan.locationtracker.utils.Constants;
import com.zayan.locationtracker.utils.ServiceUtils;

/**
 * BroadcastReceiver that fires when the device completes booting.
 *
 * Automatically restarts the location tracking service if:
 *  1. Tracking was active when the device last shut down / rebooted
 *     (persisted via AppSettings.setWasTracking(true))
 *  2. Location permission is still granted
 *
 * This gives the app "always-on tracking" behavior — the user starts
 * tracking once and it resumes automatically after every reboot without
 * requiring them to reopen the app.
 *
 * Declared in AndroidManifest.xml with:
 *   action: android.intent.action.BOOT_COMPLETED
 *   action: android.intent.action.QUICKBOOT_POWERON  (HTC devices)
 *   exported: true  (must receive system broadcast)
 *
 * Execution constraints:
 *  - onReceive() runs on the main thread with a 10-second limit
 *  - We do minimal work: read SharedPreferences + start a service
 *  - No goAsync() needed — our work completes well within the window
 *  - No database queries in onReceive() — those belong in the service
 *
 * Note on Direct Boot:
 *  This receiver is NOT directBootAware. SharedPreferences (credential-
 *  encrypted storage) is unavailable before the user unlocks the device.
 *  BOOT_COMPLETED fires after the first unlock, so our prefs are accessible.
 *  If Direct Boot support is ever needed, migrate settings to device-encrypted
 *  storage (Context.createDeviceProtectedStorageContext()).
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = Constants.LOG_TAG + "/BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            Log.w(TAG, "onReceive: null intent — ignoring.");
            return;
        }

        String action = intent.getAction();
        Log.d(TAG, "onReceive: action=" + action);

        // Guard: only handle boot-related actions.
        // While only boot actions are registered in the manifest, being
        // explicit here protects against future manifest changes.
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            Log.d(TAG, "onReceive: unrecognised action — ignoring.");
            return;
        }

        // Check 1: Was tracking active before the reboot?
        AppSettings settings = AppSettings.getInstance(context);
        if (!settings.wasTracking()) {
            Log.d(TAG, "onReceive: tracking was not active before reboot — not restarting.");
            return;
        }

        // Check 2: Does the app still have location permission?
        // The user may have revoked permissions via system settings while the
        // device was off. Starting the service without permission would cause
        // a SecurityException when the service tries to request location updates.
        if (!hasLocationPermission(context)) {
            Log.w(TAG, "onReceive: location permission no longer granted. " +
                    "Clearing wasTracking flag — user must re-enable from the app.");
            // Clear the flag so we don't keep attempting on every reboot.
            settings.setWasTracking(false);
            return;
        }

        // All checks passed — restart the tracking service.
        Log.d(TAG, "onReceive: restarting tracking service after boot.");
        ServiceUtils.startTracking(context);
    }

    /**
     * Check whether the app still holds at least fine or coarse location permission.
     *
     * We don't check background location here — if fine location is granted,
     * the foreground service can still run and track. The service itself will
     * handle the degraded-accuracy case if background location was revoked.
     *
     * @param context Application context from onReceive().
     * @return true if at least one location permission is granted.
     */
    private boolean hasLocationPermission(Context context) {
        int fine = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION);
        int coarse = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION);

        return fine == PackageManager.PERMISSION_GRANTED
                || coarse == PackageManager.PERMISSION_GRANTED;
    }
}
