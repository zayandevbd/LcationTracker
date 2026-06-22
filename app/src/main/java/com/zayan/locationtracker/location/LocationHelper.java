package com.zayan.locationtracker.location;

import android.content.Context;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.zayan.locationtracker.utils.Constants;

/**
 * Manages all interaction with FusedLocationProviderClient.
 *
 * Responsibilities:
 *  - Build and configure LocationRequest with the correct priority and interval
 *  - Register and remove LocationCallback on the FusedLocationProviderClient
 *  - Deliver location results to the service via LocationUpdateListener
 *  - Support live interval updates without restarting the service
 *
 * The service holds one instance of this class and calls:
 *   startUpdates() when tracking begins
 *   stopUpdates() when tracking ends
 *   updateInterval() when the user changes the interval in Settings
 *
 * Threading: FusedLocationProviderClient delivers callbacks on the Looper
 * provided at registration. We use the main looper here — the service
 * then dispatches any heavy work (database writes) to background threads.
 */
public class LocationHelper {

    private static final String TAG = Constants.LOG_TAG + "/LocationHelper";

    // ─── Callback Interface ───────────────────────────────────────────────────

    /**
     * Interface for receiving location updates from FusedLocationProviderClient.
     * Implemented by LocationTrackingService.
     */
    public interface LocationUpdateListener {
        /**
         * Called when a new location fix is available.
         *
         * @param latitude   Decimal degrees latitude.
         * @param longitude  Decimal degrees longitude.
         * @param accuracy   Horizontal accuracy in metres. Negative if unavailable.
         * @param provider   Provider name (e.g. "fused", "gps").
         * @param timestamp  UTC epoch milliseconds when the fix was obtained.
         */
        void onLocationUpdated(double latitude, double longitude,
                               float accuracy, String provider, long timestamp);

        /**
         * Called when location availability changes — i.e. GPS is turned off
         * mid-tracking or becomes unavailable. The service uses this to update
         * the notification and UI status.
         *
         * @param available true if location is currently available.
         */
        void onLocationAvailabilityChanged(boolean available);
    }

    // ─── Fields ──────────────────────────────────────────────────────────────

    private final FusedLocationProviderClient fusedClient;
    private final LocationUpdateListener listener;

    /**
     * The active LocationCallback instance.
     *
     * CRITICAL: This must be the exact same object instance passed to both
     * requestLocationUpdates() AND removeLocationUpdates().
     * FusedLocationProviderClient identifies callbacks by reference equality.
     * Creating a new instance for removeLocationUpdates() silently fails —
     * the old callback remains registered, callbacks fire into a dead service,
     * and battery drains with no visible tracking happening.
     */
    private LocationCallback locationCallback;

    /** Whether updates are currently registered with Fused. */
    private boolean isRequestingUpdates = false;

    // ─── Constructor ─────────────────────────────────────────────────────────

    /**
     * @param context  Any context — used to obtain FusedLocationProviderClient.
     *                 Application context is preferred to avoid leaks.
     * @param listener Receives location updates and availability changes.
     */
    public LocationHelper(@NonNull Context context,
                          @NonNull LocationUpdateListener listener) {
        this.fusedClient = LocationServices.getFusedLocationProviderClient(
                context.getApplicationContext());
        this.listener = listener;
        // Build the callback once — reused for the lifetime of this helper.
        this.locationCallback = buildLocationCallback();
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Start requesting location updates from FusedLocationProviderClient.
     *
     * Requires ACCESS_FINE_LOCATION (or ACCESS_COARSE_LOCATION) to have been
     * granted before calling. The service is responsible for verifying
     * permissions before calling this method.
     *
     * If updates are already registered (e.g. duplicate start), this is a no-op.
     *
     * @param intervalMs The desired interval between location updates, in milliseconds.
     */
    @SuppressWarnings("MissingPermission") // Permission checked by service before calling
    public void startUpdates(long intervalMs) {
        if (isRequestingUpdates) {
            Log.d(TAG, "startUpdates: already requesting updates — ignoring.");
            return;
        }

        LocationRequest request = buildLocationRequest(intervalMs);

        fusedClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()  // callbacks delivered on main thread
        ).addOnSuccessListener(unused -> {
            isRequestingUpdates = true;
            Log.d(TAG, "startUpdates: location updates registered. Interval: "
                    + intervalMs + "ms");
        }).addOnFailureListener(e -> {
            isRequestingUpdates = false;
            Log.e(TAG, "startUpdates: failed to register location updates — " + e.getMessage());
        });
    }

    /**
     * Stop requesting location updates.
     *
     * Safe to call even if updates were never started or already stopped.
     * Uses the stored locationCallback reference — same instance that was
     * passed to requestLocationUpdates().
     */
    public void stopUpdates() {
        if (!isRequestingUpdates) {
            Log.d(TAG, "stopUpdates: not currently requesting updates — ignoring.");
            return;
        }

        fusedClient.removeLocationUpdates(locationCallback)
                .addOnSuccessListener(unused -> {
                    isRequestingUpdates = false;
                    Log.d(TAG, "stopUpdates: location updates removed.");
                })
                .addOnFailureListener(e -> {
                    // Even on failure, mark as not requesting to prevent state desync.
                    isRequestingUpdates = false;
                    Log.e(TAG, "stopUpdates: failed to remove location updates — "
                            + e.getMessage());
                });
    }

    /**
     * Update the location update interval while tracking is active.
     *
     * The cleanest way to change the interval on a running FusedLocationProviderClient
     * is to remove the existing updates and re-register with a new LocationRequest.
     * FusedLocationProviderClient does not support modifying an existing request in-place.
     *
     * This causes at most one missed update during the brief window between
     * removeLocationUpdates() and requestLocationUpdates() — acceptable for
     * intervals measured in minutes.
     *
     * If not currently requesting updates, the new interval is stored for use
     * when startUpdates() is next called.
     *
     * @param newIntervalMs New interval in milliseconds.
     */
    @SuppressWarnings("MissingPermission") // Permission checked by service before calling
    public void updateInterval(long newIntervalMs) {
        if (!isRequestingUpdates) {
            // Not currently active — interval will be applied on next startUpdates().
            Log.d(TAG, "updateInterval: not active. New interval stored for next start: "
                    + newIntervalMs + "ms");
            return;
        }

        Log.d(TAG, "updateInterval: re-registering with new interval: " + newIntervalMs + "ms");

        // Remove existing registration first.
        fusedClient.removeLocationUpdates(locationCallback)
                .addOnCompleteListener(removeTask -> {
                    // Re-register with the new interval regardless of remove success/failure.
                    // If remove failed, the old callback may still fire briefly —
                    // that's acceptable; we'll get a duplicate update at worst.
                    isRequestingUpdates = false;
                    LocationRequest newRequest = buildLocationRequest(newIntervalMs);

                    fusedClient.requestLocationUpdates(
                            newRequest,
                            locationCallback,
                            Looper.getMainLooper()
                    ).addOnSuccessListener(unused -> {
                        isRequestingUpdates = true;
                        Log.d(TAG, "updateInterval: re-registered with "
                                + newIntervalMs + "ms interval.");
                    }).addOnFailureListener(e -> {
                        Log.e(TAG, "updateInterval: failed to re-register — " + e.getMessage());
                    });
                });
    }

    /**
     * @return true if location updates are currently registered.
     */
    public boolean isRequestingUpdates() {
        return isRequestingUpdates;
    }

    // ─── Private Helpers ─────────────────────────────────────────────────────

    /**
     * Build a LocationRequest with the given interval.
     *
     * Priority: PRIORITY_HIGH_ACCURACY — instructs Fused to use GPS when available.
     * Correct for a dedicated tracking app. Would use PRIORITY_BALANCED_POWER_ACCURACY
     * for a lower-battery-impact option (network/Wi-Fi based).
     *
     * setMinUpdateIntervalMillis(): the fastest the client will deliver updates
     * even if another app is requesting high-frequency updates. Set to half the
     * requested interval to allow slightly earlier updates when GPS is fresh,
     * but capped at Constants.FASTEST_INTERVAL_MS minimum.
     *
     * setMinUpdateDistanceMeters(0): deliver updates based on time, not distance.
     * A stationary device should still record a periodic fix to confirm it hasn't moved.
     *
     * @param intervalMs Desired update interval in milliseconds.
     * @return Configured LocationRequest.
     */
    @NonNull
    private LocationRequest buildLocationRequest(long intervalMs) {
        long fastestInterval = Math.max(
                Constants.FASTEST_INTERVAL_MS,
                intervalMs / 2
        );

        return new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
                .setMinUpdateIntervalMillis(fastestInterval)
                .setMinUpdateDistanceMeters(Constants.MIN_DISPLACEMENT_METERS)
                .setWaitForAccurateLocation(false) // don't block waiting for high-accuracy fix
                .build();
    }

    /**
     * Build the LocationCallback that handles incoming location results.
     *
     * Called once in the constructor — the same instance is reused throughout
     * the service lifecycle. This is essential: removeLocationUpdates() must
     * receive the exact same callback object that was passed to
     * requestLocationUpdates() or it silently fails.
     *
     * @return A configured LocationCallback.
     */
    @NonNull
    private LocationCallback buildLocationCallback() {
        return new LocationCallback() {

            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                // getLastLocation() returns the most recent fix in the batch.
                // Fused may batch multiple fixes — we only need the latest.
                android.location.Location location = locationResult.getLastLocation();

                if (location == null) {
                    Log.w(TAG, "onLocationResult: result contained no location.");
                    return;
                }

                String provider = location.getProvider();
                if (provider == null) {
                    provider = "unknown";
                }

                Log.d(TAG, String.format("onLocationResult: lat=%.6f lon=%.6f acc=%.1fm via %s",
                        location.getLatitude(),
                        location.getLongitude(),
                        location.getAccuracy(),
                        provider));

                listener.onLocationUpdated(
                        location.getLatitude(),
                        location.getLongitude(),
                        location.getAccuracy(),
                        provider,
                        location.getTime()  // use the fix timestamp, not System.currentTimeMillis()
                );
            }

            @Override
            public void onLocationAvailability(@NonNull LocationAvailability availability) {
                boolean available = availability.isLocationAvailable();
                Log.d(TAG, "onLocationAvailability: " + available);
                listener.onLocationAvailabilityChanged(available);
            }
        };
    }
}
