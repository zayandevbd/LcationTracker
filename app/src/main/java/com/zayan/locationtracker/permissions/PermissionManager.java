package com.zayan.locationtracker.permissions;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
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

/**
 * Manages the multi-step runtime permission flow for location tracking.
 *
 * Android's location permission model requires sequential requests:
 *
 *   Step 1 — Fine + Coarse location (API 24+, always required)
 *   Step 2 — Background location (API 29+, must be requested SEPARATELY after Step 1)
 *   Step 3 — POST_NOTIFICATIONS (API 33+, for the foreground service notification)
 *
 * On API 30+, requesting ACCESS_BACKGROUND_LOCATION in the same call as
 * ACCESS_FINE_LOCATION causes Android to silently drop the background request.
 * This class enforces the correct sequential flow automatically.
 *
 * Usage:
 *   permissionManager = new PermissionManager(activity, callback);
 *   permissionManager.registerLaunchers(); // call in onCreate() BEFORE setContentView
 *   permissionManager.requestAllPermissions(); // call when ready
 */
public class PermissionManager {

    private static final String TAG = Constants.LOG_TAG + "/PermissionManager";

    // ─── Callback Interface ───────────────────────────────────────────────────

    /**
     * Callback interface for permission flow results.
     * The Activity implements this to react without knowing the internals
     * of how permissions were requested.
     */
    public interface PermissionCallback {
        /**
         * Called when all required permissions are granted (or already were).
         * Safe to start the tracking service now.
         */
        void onAllPermissionsGranted();

        /**
         * Called when at least one critical permission was denied.
         * Tracking cannot proceed.
         *
         * @param isPermanentlyDenied true if the user checked "Don't ask again"
         *                            — show a dialog directing them to Settings.
         */
        void onPermissionDenied(boolean isPermanentlyDenied);

        /**
         * Called when background location was denied but fine location was granted.
         * Tracking will work only while the app is in the foreground.
         *
         * @param isPermanentlyDenied true if permanently denied.
         */
        void onBackgroundLocationDenied(boolean isPermanentlyDenied);
    }

    // ─── Fields ──────────────────────────────────────────────────────────────

    private final AppCompatActivity activity;
    private final PermissionCallback callback;

    // ActivityResultLaunchers — must be registered in onCreate() before
    // the Activity reaches STARTED state, per the ActivityResult API contract.
    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private ActivityResultLauncher<String> backgroundLocationLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;

    // ─── Constructor ─────────────────────────────────────────────────────────

    /**
     * @param activity The host Activity. Must be an AppCompatActivity.
     * @param callback Receives results of the permission flow.
     */
    public PermissionManager(@NonNull AppCompatActivity activity,
                             @NonNull PermissionCallback callback) {
        this.activity = activity;
        this.callback = callback;
    }

    // ─── Launcher Registration ────────────────────────────────────────────────

    /**
     * Register all ActivityResultLaunchers.
     *
     * MUST be called in Activity.onCreate() BEFORE the Activity reaches the
     * STARTED lifecycle state. Calling this after onStart() throws an exception.
     * Typically called as the first line of onCreate() before setContentView().
     */
    public void registerLaunchers() {
        // Step 1: Fine + Coarse location
        locationPermissionLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean fineGranted = Boolean.TRUE.equals(
                            result.get(Manifest.permission.ACCESS_FINE_LOCATION));
                    boolean coarseGranted = Boolean.TRUE.equals(
                            result.get(Manifest.permission.ACCESS_COARSE_LOCATION));

                    if (fineGranted || coarseGranted) {
                        Log.d(TAG, "Location permission granted. Proceeding to background request.");
                        // Location granted — now request background location if needed.
                        requestBackgroundLocationIfNeeded();
                    } else {
                        // Denied — check if permanently denied.
                        boolean permanent = !activity.shouldShowRequestPermissionRationale(
                                Manifest.permission.ACCESS_FINE_LOCATION);
                        Log.w(TAG, "Location permission denied. Permanent: " + permanent);
                        callback.onPermissionDenied(permanent);
                    }
                }
        );

        // Step 2: Background location (API 29+ only)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundLocationLauncher = activity.registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) {
                            Log.d(TAG, "Background location granted. Proceeding to notification.");
                            requestNotificationPermissionIfNeeded();
                        } else {
                            boolean permanent = !activity.shouldShowRequestPermissionRationale(
                                    Manifest.permission.ACCESS_BACKGROUND_LOCATION);
                            Log.w(TAG, "Background location denied. Permanent: " + permanent);
                            // Not a fatal denial — foreground tracking still works.
                            // Inform the caller and still proceed to notification permission.
                            callback.onBackgroundLocationDenied(permanent);
                            requestNotificationPermissionIfNeeded();
                        }
                    }
            );
        }

        // Step 3: POST_NOTIFICATIONS (API 33+ only)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher = activity.registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        // Notification permission result — not blocking.
                        // The foreground service still runs without it (notification just won't show).
                        // We proceed regardless of the result.
                        if (!granted) {
                            Log.w(TAG, "Notification permission denied. " +
                                    "Foreground service notification will not be shown.");
                        }
                        Log.d(TAG, "Permission flow complete. All critical permissions handled.");
                        callback.onAllPermissionsGranted();
                    }
            );
        }
    }

    // ─── Public Entry Point ───────────────────────────────────────────────────

    /**
     * Start the full permission request flow.
     *
     * Checks what's already granted and skips any steps that are already
     * satisfied. If all permissions are already granted, calls
     * onAllPermissionsGranted() immediately without showing any dialog.
     *
     * Call this when the user taps "Start Tracking" or on first launch.
     */
    public void requestAllPermissions() {
        if (hasLocationPermission()) {
            Log.d(TAG, "Fine location already granted — skipping Step 1.");
            requestBackgroundLocationIfNeeded();
        } else {
            requestLocationPermissions();
        }
    }

    // ─── Permission Checks ────────────────────────────────────────────────────

    /**
     * Check if the app has at least fine location permission.
     */
    public boolean hasLocationPermission() {
        return isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
                || isGranted(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    /**
     * Check if background location permission is granted.
     * Always returns true on API < 29 (permission doesn't exist).
     */
    public boolean hasBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        }
        return true;
    }

    /**
     * Check if notification permission is granted.
     * Always returns true on API < 33 (permission doesn't exist).
     */
    public boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return isGranted(Manifest.permission.POST_NOTIFICATIONS);
        }
        return true;
    }

    /**
     * Returns true if all critical permissions for background tracking are granted.
     * Use this as a pre-check before starting the service.
     */
    public boolean hasAllRequiredPermissions() {
        return hasLocationPermission() && hasBackgroundLocationPermission();
    }

    // ─── Step 1: Location ─────────────────────────────────────────────────────

    private void requestLocationPermissions() {
        if (activity.shouldShowRequestPermissionRationale(
                Manifest.permission.ACCESS_FINE_LOCATION)) {
            // User denied once before — show rationale explaining why we need this.
            showRationaleDialog(
                    activity.getString(R.string.permission_location_rationale_title),
                    activity.getString(R.string.permission_location_rationale_message),
                    () -> launchLocationRequest()
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

    // ─── Step 2: Background Location ─────────────────────────────────────────

    private void requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && !hasBackgroundLocationPermission()) {

            if (activity.shouldShowRequestPermissionRationale(
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                showRationaleDialog(
                        activity.getString(R.string.permission_background_rationale_title),
                        activity.getString(R.string.permission_background_rationale_message),
                        () -> launchBackgroundLocationRequest()
                );
            } else {
                launchBackgroundLocationRequest();
            }
        } else {
            // Already granted or not required — move to next step.
            requestNotificationPermissionIfNeeded();
        }
    }

    private void launchBackgroundLocationRequest() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && backgroundLocationLauncher != null) {
            backgroundLocationLauncher.launch(
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        }
    }

    // ─── Step 3: Notifications ────────────────────────────────────────────────

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !hasNotificationPermission()) {
            if (notificationPermissionLauncher != null) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            // Already granted or not required — flow complete.
            Log.d(TAG, "All permissions satisfied.");
            callback.onAllPermissionsGranted();
        }
    }

    // ─── Settings Redirect ────────────────────────────────────────────────────

    /**
     * Open the app's system permission settings page.
     * Call this when a permission is permanently denied.
     *
     * The user must manually re-enable permissions here — we cannot
     * request them programmatically once they're permanently denied.
     */
    public void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private boolean isGranted(@NonNull String permission) {
        return ContextCompat.checkSelfPermission(activity, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Show a rationale dialog before requesting a permission.
     * Uses a simple AlertDialog with a single "Continue" action.
     *
     * @param title    Dialog title.
     * @param message  Explanation message.
     * @param onAccept Runnable invoked when user taps "Continue".
     */
    private void showRationaleDialog(@NonNull String title,
                                     @NonNull String message,
                                     @NonNull Runnable onAccept) {
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.dialog_continue, (dialog, which) -> onAccept.run())
                .setNegativeButton(R.string.dialog_cancel, (dialog, which) -> {
                    // User declined the rationale — treat as a denial.
                    callback.onPermissionDenied(false);
                })
                .setCancelable(false)
                .show();
    }
}
