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

public class ServiceRestartReceiver extends BroadcastReceiver {

    private static final String TAG = Constants.LOG_TAG + "/ServiceRestartReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            Log.w(TAG, "onReceive: null intent — ignoring.");
            return;
        }

        if (!Constants.ACTION_RESTART_SERVICE.equals(intent.getAction())) {
            Log.d(TAG, "onReceive: unexpected action [" + intent.getAction() + "] — ignoring.");
            return;
        }

        AppSettings settings = AppSettings.getInstance(context);
        if (!settings.wasTracking()) {
            Log.d(TAG, "onReceive: wasTracking=false — not restarting.");
            return;
        }

        if (!hasLocationPermission(context)) {
            Log.w(TAG, "onReceive: location permission not granted — cannot restart.");
            settings.setWasTracking(false);
            return;
        }

        Log.d(TAG, "onReceive: restarting LocationTrackingService.");
        ServiceUtils.startTracking(context);
    }

    private boolean hasLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
}
