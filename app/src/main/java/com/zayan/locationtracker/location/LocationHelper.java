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

public class LocationHelper {

    private static final String TAG = Constants.LOG_TAG + "/LocationHelper";

    public interface LocationUpdateListener {
        void onLocationUpdated(double latitude, double longitude,
                               float accuracy, String provider, long timestamp);
        void onLocationAvailabilityChanged(boolean available);
    }

    private final FusedLocationProviderClient fusedClient;
    private final LocationUpdateListener listener;
    private LocationCallback locationCallback;

    private boolean isRequestingUpdates = false;

    public LocationHelper(@NonNull Context context, @NonNull LocationUpdateListener listener) {
        this.fusedClient = LocationServices.getFusedLocationProviderClient(
                context.getApplicationContext());
        this.listener = listener;
        this.locationCallback = buildLocationCallback();
    }

    @SuppressWarnings("MissingPermission") 
    public void startUpdates(long intervalMs) {
        if (isRequestingUpdates) {
            Log.d(TAG, "startUpdates: already active — ignoring.");
            return;
        }
        fusedClient.requestLocationUpdates(
                buildLocationRequest(intervalMs),
                locationCallback,
                Looper.getMainLooper()
        ).addOnSuccessListener(unused -> {
            isRequestingUpdates = true;
            Log.d(TAG, "startUpdates: registered at " + intervalMs + "ms");
        }).addOnFailureListener(e -> {
            isRequestingUpdates = false;
            Log.e(TAG, "startUpdates: failed — " + e.getMessage());
        });
    }

    @SuppressWarnings("MissingPermission")
    public void stopUpdates() {
        if (!isRequestingUpdates) {
            Log.d(TAG, "stopUpdates: not active — ignoring.");
            return;
        }
        fusedClient.removeLocationUpdates(locationCallback)
                .addOnSuccessListener(unused -> {
                    isRequestingUpdates = false;
                    Log.d(TAG, "stopUpdates: updates removed.");
                })
                .addOnFailureListener(e -> {
                    isRequestingUpdates = false;
                    Log.e(TAG, "stopUpdates: failed — " + e.getMessage());
                });
    }

    @SuppressWarnings("MissingPermission") 
    public void updateInterval(long newIntervalMs) {
        if (!isRequestingUpdates) {
            Log.d(TAG, "updateInterval: not active, interval will apply on next start.");
            return;
        }
        // remove then re-register — same instance required, Fused matches by reference
        fusedClient.removeLocationUpdates(locationCallback)
                .addOnCompleteListener(removeTask -> {
                    isRequestingUpdates = false;
                    fusedClient.requestLocationUpdates(
                            buildLocationRequest(newIntervalMs),
                            locationCallback,
                            Looper.getMainLooper()
                    ).addOnSuccessListener(unused -> {
                        isRequestingUpdates = true;
                        Log.d(TAG, "updateInterval: re-registered at " + newIntervalMs + "ms");
                    }).addOnFailureListener(e ->
                            Log.e(TAG, "updateInterval: re-register failed — " + e.getMessage())
                    );
                });
    }

    public boolean isRequestingUpdates() {
        return isRequestingUpdates;
    }

    private LocationRequest buildLocationRequest(long intervalMs) {
        long fastestInterval = Math.max(Constants.FASTEST_INTERVAL_MS, intervalMs / 2);
        return new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
                .setMinUpdateIntervalMillis(fastestInterval)
                .setMinUpdateDistanceMeters(Constants.MIN_DISPLACEMENT_METERS)
                .setWaitForAccurateLocation(false)
                .build();
    }

    @NonNull
    private LocationCallback buildLocationCallback() {
        return new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                android.location.Location location = locationResult.getLastLocation();
                if (location == null) {
                    Log.w(TAG, "onLocationResult: no location in result.");
                    return;
                }
                String provider = location.getProvider();
                if (provider == null) provider = "unknown";

                Log.d(TAG, String.format("onLocationResult: %.6f, %.6f ±%.1fm via %s",
                        location.getLatitude(), location.getLongitude(),
                        location.getAccuracy(), provider));

                listener.onLocationUpdated(
                        location.getLatitude(),
                        location.getLongitude(),
                        location.getAccuracy(),
                        provider,
                        location.getTime() // fix timestamp, not System.currentTimeMillis()
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
