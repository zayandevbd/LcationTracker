package com.zayan.locationtracker.ui.main;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.zayan.locationtracker.R;
import com.zayan.locationtracker.databinding.ActivityMainBinding;
import com.zayan.locationtracker.permissions.PermissionManager;
import com.zayan.locationtracker.repository.LocationRepository;
import com.zayan.locationtracker.settings.AppSettings;
import com.zayan.locationtracker.ui.history.HistoryActivity;
import com.zayan.locationtracker.ui.settings.SettingsActivity;
import com.zayan.locationtracker.utils.Constants;
import com.zayan.locationtracker.utils.ServiceUtils;
import com.zayan.locationtracker.viewmodel.LocationViewModel;
import com.zayan.locationtracker.viewmodel.LocationViewModelFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Main screen — entry point and primary control surface for location tracking.
 *
 * Responsibilities:
 *  - Request all required permissions via PermissionManager
 *  - Start / stop the LocationTrackingService via ServiceUtils
 *  - Observe ViewModel LiveData to keep UI in sync
 *  - Register/unregister LocalBroadcast receiver for real-time service updates
 *  - Check GPS state on resume and prompt user if disabled
 *  - Navigate to HistoryActivity and SettingsActivity
 *  - Show battery optimization guidance
 *
 * The Activity owns no tracking logic — it delegates everything to the
 * ViewModel and ServiceUtils, keeping itself as a pure UI controller.
 */
public class MainActivity extends AppCompatActivity
        implements PermissionManager.PermissionCallback {

    private static final String TAG = Constants.LOG_TAG + "/MainActivity";

    // ─── View Binding & ViewModel ─────────────────────────────────────────────

    private ActivityMainBinding binding;
    private LocationViewModel viewModel;
    private PermissionManager permissionManager;
    private AppSettings appSettings;

    private final SimpleDateFormat timeFormatter =
            new SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault());

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // ── CRITICAL: register launchers BEFORE setContentView ──
        // ActivityResultLauncher must be registered before Activity reaches STARTED.
        permissionManager = new PermissionManager(this, this);
        permissionManager.registerLaunchers();

        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        appSettings = AppSettings.getInstance(this);

        setupViewModel();
        setupClickListeners();
        observeViewModel();

        // Sync UI with actual service state on (re)launch.
        syncTrackingState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerServiceBroadcastReceiver();
        syncTrackingState();
        updateIntervalDisplay();

        // Check GPS — user may have disabled it while we were backgrounded.
        if (viewModel.isCurrentlyTracking() && !ServiceUtils.isLocationEnabled(this)) {
            showGpsDisabledDialog();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterServiceBroadcastReceiver();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    // ─── ViewModel Setup ──────────────────────────────────────────────────────

    private void setupViewModel() {
        LocationRepository repository = LocationRepository.getInstance(this);
        LocationViewModelFactory factory = new LocationViewModelFactory(repository);
        viewModel = new ViewModelProvider(this, factory).get(LocationViewModel.class);
    }

    private void observeViewModel() {
        // Tracking state → update button labels and status chip.
        viewModel.isTracking().observe(this, isTracking -> {
            updateTrackingUI(isTracking != null && isTracking);
        });

        // Latest location → update the coordinates display.
        viewModel.getLatestLocation().observe(this, location -> {
            if (location != null) {
                String timeStr = timeFormatter.format(new Date(location.getTimestamp()));
                binding.tvCurrentLat.setText(
                        getString(R.string.label_latitude, location.getLatitude()));
                binding.tvCurrentLon.setText(
                        getString(R.string.label_longitude, location.getLongitude()));
                binding.tvCurrentTime.setText(
                        getString(R.string.label_time, timeStr));
                binding.tvCurrentAccuracy.setText(
                        getString(R.string.label_accuracy, location.getAccuracy()));
                binding.cardLocationData.setVisibility(View.VISIBLE);
            }
        });

        // Status messages → show as a dialog or Snackbar.
        viewModel.getStatusMessage().observe(this, message -> {
            if (message != null && !message.isEmpty()) {
                showInfoSnackbar(message);
                viewModel.clearStatusMessage();
            }
        });

        // Current interval → update interval display.
        viewModel.getCurrentIntervalMs().observe(this, intervalMs -> {
            updateIntervalDisplay();
        });
    }

    // ─── Click Listeners ─────────────────────────────────────────────────────

    private void setupClickListeners() {
        binding.btnStartTracking.setOnClickListener(v -> onStartTrackingClicked());
        binding.btnStopTracking.setOnClickListener(v -> onStopTrackingClicked());
        binding.btnOpenHistory.setOnClickListener(v -> openHistory());
        binding.btnOpenSettings.setOnClickListener(v -> openSettings());
    }

    private void onStartTrackingClicked() {
        if (viewModel.isCurrentlyTracking()) {
            // Give the user clear feedback instead of silently ignoring the tap.
            showInfoSnackbar(getString(R.string.toast_already_tracking));
            return;
        }

        // Check GPS first — show dialog if disabled.
        if (!ServiceUtils.isLocationEnabled(this)) {
            showGpsDisabledDialog();
            return;
        }

        // Show battery optimization guidance if not already whitelisted.
        if (!ServiceUtils.isIgnoringBatteryOptimizations(this)) {
            showBatteryOptimizationDialog();
            // Continue with permission request regardless — battery whitelist
            // is advisory, not a hard requirement.
        }

        // Kick off the permission flow — service starts in onAllPermissionsGranted().
        permissionManager.requestAllPermissions();
    }

    private void onStopTrackingClicked() {
        if (!viewModel.isCurrentlyTracking()) {
            Log.d(TAG, "Not tracking — ignoring stop tap.");
            return;
        }
        ServiceUtils.stopTracking(this);
        viewModel.setTracking(false);
        updateTrackingUI(false);
    }

    // ─── PermissionManager.PermissionCallback ────────────────────────────────

    @Override
    public void onAllPermissionsGranted() {
        Log.d(TAG, "onAllPermissionsGranted: starting service.");
        ServiceUtils.startTracking(this);
        viewModel.setTracking(true);
        updateTrackingUI(true);
    }

    @Override
    public void onPermissionDenied(boolean isPermanentlyDenied) {
        Log.w(TAG, "onPermissionDenied: permanent=" + isPermanentlyDenied);
        if (isPermanentlyDenied) {
            showPermissionPermanentlyDeniedDialog();
        } else {
            showInfoSnackbar(getString(R.string.permission_denied_message));
        }
    }

    @Override
    public void onBackgroundLocationDenied(boolean isPermanentlyDenied) {
        Log.w(TAG, "onBackgroundLocationDenied: permanent=" + isPermanentlyDenied);
        // Tracking continues in foreground mode — inform the user.
        showInfoSnackbar(getString(R.string.permission_background_denied_message));
    }

    // ─── UI Updates ───────────────────────────────────────────────────────────

    private void updateTrackingUI(boolean isTracking) {
        if (binding == null) return;

        binding.btnStartTracking.setEnabled(!isTracking);
        binding.btnStopTracking.setEnabled(isTracking);

        // Swap icons to reflect state clearly:
        // When tracking: Start shows location-pin (running indicator), Stop shows stop-square
        // When stopped:  Start shows play-arrow, Stop shows play-arrow (dimmed, disabled)
        if (isTracking) {
            binding.btnStartTracking.setIconResource(R.drawable.ic_location_notification);
            binding.btnStopTracking.setIconResource(R.drawable.ic_stop);
        } else {
            binding.btnStartTracking.setIconResource(R.drawable.ic_play_arrow);
            binding.btnStopTracking.setIconResource(R.drawable.ic_stop);
        }

        binding.tvTrackingStatus.setText(
                isTracking
                        ? getString(R.string.status_tracking_active)
                        : getString(R.string.status_tracking_inactive)
        );

        int statusColor = isTracking
                ? getColor(R.color.status_active)
                : getColor(R.color.status_inactive);
        binding.tvTrackingStatus.setTextColor(statusColor);

        if (!isTracking) {
            binding.cardLocationData.setVisibility(View.GONE);
        }
    }

    private void updateIntervalDisplay() {
        if (binding == null) return;
        String label = appSettings.getIntervalLabel();
        binding.tvCurrentInterval.setText(getString(R.string.label_interval, label));
    }

    private void syncTrackingState() {
        boolean serviceRunning = ServiceUtils.isServiceRunning(this);
        viewModel.setTracking(serviceRunning);
        updateTrackingUI(serviceRunning);
    }

    // ─── Dialogs ──────────────────────────────────────────────────────────────

    private void showGpsDisabledDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_gps_disabled_title)
                .setMessage(R.string.dialog_gps_disabled_message)
                .setPositiveButton(R.string.dialog_open_settings, (d, w) ->
                        startActivity(ServiceUtils.getLocationSettingsIntent()))
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void showBatteryOptimizationDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_battery_title)
                .setMessage(R.string.dialog_battery_message)
                .setPositiveButton(R.string.dialog_open_settings, (d, w) ->
                        startActivity(ServiceUtils.getBatteryOptimizationSettingsIntent(this)))
                .setNegativeButton(R.string.dialog_skip, null)
                .show();
    }

    private void showPermissionPermanentlyDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_permission_denied_title)
                .setMessage(R.string.dialog_permission_denied_message)
                .setPositiveButton(R.string.dialog_open_settings,
                        (d, w) -> permissionManager.openAppSettings())
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void showInfoSnackbar(String message) {
        com.google.android.material.snackbar.Snackbar
                .make(binding.getRoot(), message,
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                .show();
    }

    // ─── Navigation ───────────────────────────────────────────────────────────

    private void openHistory() {
        startActivity(new Intent(this, HistoryActivity.class));
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    // ─── LocalBroadcast Receiver ─────────────────────────────────────────────

    /**
     * Receives live location updates and tracking status changes from the service.
     * Registered in onResume(), unregistered in onPause() — active only while
     * the Activity is visible.
     */
    private final BroadcastReceiver serviceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();

            if (Constants.ACTION_LOCATION_UPDATE.equals(action)) {
                double lat = intent.getDoubleExtra(Constants.EXTRA_LATITUDE, 0.0);
                double lon = intent.getDoubleExtra(Constants.EXTRA_LONGITUDE, 0.0);
                float accuracy = intent.getFloatExtra(Constants.EXTRA_ACCURACY, 0f);
                long timestamp = intent.getLongExtra(Constants.EXTRA_TIMESTAMP, 0L);
                String timeStr = timeFormatter.format(new Date(timestamp));

                if (binding != null) {
                    binding.tvCurrentLat.setText(
                            getString(R.string.label_latitude, lat));
                    binding.tvCurrentLon.setText(
                            getString(R.string.label_longitude, lon));
                    binding.tvCurrentTime.setText(
                            getString(R.string.label_time, timeStr));
                    binding.tvCurrentAccuracy.setText(
                            getString(R.string.label_accuracy, accuracy));
                    binding.cardLocationData.setVisibility(View.VISIBLE);
                }

            } else if (Constants.ACTION_TRACKING_STATUS_CHANGED.equals(action)) {
                boolean isTracking = intent.getBooleanExtra(Constants.EXTRA_IS_TRACKING, false);
                viewModel.setTracking(isTracking);
                updateTrackingUI(isTracking);
            }
        }
    };

    private void registerServiceBroadcastReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Constants.ACTION_LOCATION_UPDATE);
        filter.addAction(Constants.ACTION_TRACKING_STATUS_CHANGED);
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(serviceReceiver, filter);
        Log.d(TAG, "Service broadcast receiver registered.");
    }

    private void unregisterServiceBroadcastReceiver() {
        LocalBroadcastManager.getInstance(this)
                .unregisterReceiver(serviceReceiver);
        Log.d(TAG, "Service broadcast receiver unregistered.");
    }
}
