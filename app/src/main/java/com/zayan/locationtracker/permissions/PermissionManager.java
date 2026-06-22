package com.zayan.locationtracker.permissions;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.zayan.locationtracker.R;
import com.zayan.locationtracker.utils.Constants;

public class PermissionManager {

    private static final String TAG = Constants.LOG_TAG + "/PermissionManager";

    public interface PermissionCallback {
        void onAllPermissionsGranted();
        void onPermissionDenied(boolean isPermanentlyDenied);
        void onBackgroundLocationDenied(boolean isPermanentlyDenied);
    }

    private final AppCompatActivity activity;
    private final PermissionCallback callback;

    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private ActivityResultLauncher<String> backgroundLocationLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;

    public PermissionManager(@NonNull AppCompatActivity activity,
                             @NonNull PermissionCallback callback) {
        this.activity = activity;
        this.callback = callback;
    }

    public void registerLaunchers() {
        locationPermissionLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean fineGranted = Boolean.TRUE.equals(
                            result.get(Manifest.permission.ACCESS_FINE_LOCATION));
                    boolean coarseGranted = Boolean.TRUE.equals(
                            result.get(Manifest.permission.ACCESS_COARSE_LOCATION));

                    if (fineGranted || coarseGranted) {
                        Log.d(TAG, "Location permission granted.");
                        requestBackgroundLocationIfNeeded();
                    } else {
                        boolean permanent = !activity.shouldShowRequestPermissionRationale(
                                Manifest.permission.ACCESS_FINE_LOCATION);
                        Log.w(TAG, "Location permission denied. Permanent: " + permanent);
                        callback.onPermissionDenied(permanent);
                    }
                }
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundLocationLauncher = activity.registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) {
                            Log.d(TAG, "Background location granted.");
                            requestNotificationPermissionIfNeeded();
                        } else {
                            boolean permanent = !activity.shouldShowRequestPermissionRationale(
                                    Manifest.permission.ACCESS_BACKGROUND_LOCATION);
                            Log.w(TAG, "Background location denied. Permanent: " + permanent);
                            callback.onBackgroundLocationDenied(permanent);
                            requestNotificationPermissionIfNeeded();
                        }
                    }
            );
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher = activity.registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (!granted) {
                            Log.w(TAG, "Notification permission denied — foreground notification won't show.");
                        }
                        callback.onAllPermissionsGranted();
                    }
            );
        }
    }

    public void requestAllPermissions() {
        if (hasLocationPermission()) {
            requestBackgroundLocationIfNeeded();
        } else {
            requestLocationPermissions();
        }
    }

    public boolean hasLocationPermission() {
        return isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
                || isGranted(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    public boolean hasBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        }
        return true;
    }

    public boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return isGranted(Manifest.permission.POST_NOTIFICATIONS);
        }
        return true;
    }

    public boolean hasAllRequiredPermissions() {
        return hasLocationPermission() && hasBackgroundLocationPermission();
    }

    private void requestLocationPermissions() {
        if (activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            showRationaleDialog(
                    activity.getString(R.string.permission_location_rationale_title),
                    activity.getString(R.string.permission_location_rationale_message),
                    this::launchLocationRequest
            );
        } else {
            launchLocationRequest();
        }
    }

    private void launchLocationRequest() {
        if (locationPermissionLauncher != null) {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackgroundLocationPermission()) {
            if (activity.shouldShowRequestPermissionRationale(
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                showRationaleDialog(
                        activity.getString(R.string.permission_background_rationale_title),
                        activity.getString(R.string.permission_background_rationale_message),
                        this::launchBackgroundLocationRequest
                );
            } else {
                launchBackgroundLocationRequest();
            }
        } else {
            requestNotificationPermissionIfNeeded();
        }
    }

    private void launchBackgroundLocationRequest() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && backgroundLocationLauncher != null) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !hasNotificationPermission()) {
            if (notificationPermissionLauncher != null) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            Log.d(TAG, "All permissions satisfied.");
            callback.onAllPermissionsGranted();
        }
    }

    public void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }

    private boolean isGranted(@NonNull String permission) {
        return ContextCompat.checkSelfPermission(activity, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void showRationaleDialog(@NonNull String title,
                                     @NonNull String message,
                                     @NonNull Runnable onAccept) {
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.dialog_continue, (dialog, which) -> onAccept.run())
                .setNegativeButton(R.string.dialog_cancel, (dialog, which) ->
                        callback.onPermissionDenied(false))
                .setCancelable(false)
                .show();
    }
}
