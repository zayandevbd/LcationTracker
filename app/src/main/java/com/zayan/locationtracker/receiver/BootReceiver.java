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

        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            return;
        }

        AppSettings settings = AppSettings.getInstance(context);
        if (!settings.wasTracking()) {
            Log.d(TAG, "onReceive: tracking was not active before reboot — not restarting.");
            return;
        }

        if (!hasLocationPermission(context)) {
            Log.w(TAG, "onReceive: location permission gone — clearing wasTracking.");
            settings.setWasTracking(false);
            return;
        }

        Log.d(TAG, "onReceive: restarting tracking service after boot.");
        ServiceUtils.startTracking(context);
    }

    private boolean hasLocationPermission(Context context) {
        int fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION);
        int coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION);
        return fine == PackageManager.PERMISSION_GRANTED
                || coarse == PackageManager.PERMISSION_GRANTED;
    }
}
