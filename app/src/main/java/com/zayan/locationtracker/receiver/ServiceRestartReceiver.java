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
 * Internal BroadcastReceiver that restarts LocationTrackingService after
 * it has been killed — typically when the user swipes the app from Recents.
 *
 * Flow:
 *  1. User swipes app from Recents (or OEM task killer fires)
 *  2. LocationTrackingService.onTaskRemoved() is called
 *  3. Service schedules an AlarmManager broadcast to this receiver
 *  4. This receiver fires ~1 second later and restarts the service
 *
 * This receiver is declared in the manifest with exported="false" —
 * it should never be triggered by anything outside this application.
 *
 * Guards before restarting:
 *  - Correct action (ACTION_RESTART_SERVICE)
 *  - User intended tracking to be active (wasTracking flag)
 *  - Location permission still granted
 *  - Service not already running (ServiceUtils.startTracking handles this)
 */
public class ServiceRestartReceiver extends BroadcastReceiver {

    private static final String TAG = Constants.LOG_TAG + "/ServiceRestartReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            Log.w(TAG, "onReceive: null intent — ignoring.");
            return;
        }

        // Guard: only handle our specific restart action.
        if (!Constants.ACTION_RESTART_SERVICE.equals(intent.getAction())) {
            Log.d(TAG, "onReceive: unexpected action [" + intent.getAction() + "] — ignoring.");
            return;
        }

        Log.d(TAG, "onReceive: restart broadcast received.");

        // Guard: only restart if user intended tracking to be running.
        // If the user explicitly stopped tracking (via Stop button or notification),
        // wasTracking() returns false and we respect that intent.
        AppSettings settings = AppSettings.getInstance(context);
        if (!settings.wasTracking()) {
            Log.d(TAG, "onReceive: wasTracking=false — user stopped tracking intentionally. " +
                    "Not restarting.");
            return;
        }

        // Guard: verify location permission is still granted.
        // It's possible (though unlikely in this short window) that the user
        // revoked permission between the alarm being scheduled and firing.
        if (!hasLocationPermission(context)) {
            Log.w(TAG, "onReceive: location permission not granted — cannot restart service.");
            settings.setWasTracking(false);
            return;
        }

        // All guards passed — restart the service.
        // ServiceUtils.startTracking() also checks isServiceRunning() internally,
        // preventing a duplicate start if the service already recovered on its own.
        Log.d(TAG, "onReceive: restarting LocationTrackingService.");
        ServiceUtils.startTracking(context);
    }

    /**
     * Quick permission check — same logic as BootReceiver.
     * Fine OR coarse location is sufficient to start the service.
     */
    private boolean hasLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
}
