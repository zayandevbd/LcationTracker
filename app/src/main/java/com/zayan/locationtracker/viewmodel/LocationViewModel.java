package com.zayan.locationtracker.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.zayan.locationtracker.database.entity.LocationEntity;
import com.zayan.locationtracker.repository.LocationRepository;
import com.zayan.locationtracker.utils.Constants;

import java.util.List;

public class LocationViewModel extends ViewModel {

    private final LocationRepository repository;

    private final MutableLiveData<Boolean> isTracking = new MutableLiveData<>(false);
    private final MutableLiveData<Long> currentIntervalMs =
            new MutableLiveData<>(Constants.DEFAULT_INTERVAL_MS);
    private final MutableLiveData<LocationEntity> latestLocation = new MutableLiveData<>(null);
    // one-shot status messages — clear after consuming to prevent reshowing on rotation
    private final MutableLiveData<String> statusMessage = new MutableLiveData<>(null);

    private final LiveData<List<LocationEntity>> locationHistory;

    LocationViewModel(@NonNull LocationRepository repository) {
        this.repository = repository;
        this.locationHistory = repository.getAllLocations();
        loadLatestLocationFromDb();
    }

    private void loadLatestLocationFromDb() {
        repository.getLatestLocation(location -> {
            if (location != null) {
                latestLocation.postValue(location); // callback runs on bg thread
            }
        });
    }

    public LiveData<Boolean> isTracking() { return isTracking; }
    public LiveData<Long> getCurrentIntervalMs() { return currentIntervalMs; }
    public LiveData<LocationEntity> getLatestLocation() { return latestLocation; }
    public LiveData<String> getStatusMessage() { return statusMessage; }
    public LiveData<List<LocationEntity>> getLocationHistory() { return locationHistory; }

    public void setTracking(boolean tracking) { isTracking.setValue(tracking); }
    public void postTracking(boolean tracking) { isTracking.postValue(tracking); }
    public void setCurrentIntervalMs(long intervalMs) { currentIntervalMs.setValue(intervalMs); }
    public void postLatestLocation(@NonNull LocationEntity location) { latestLocation.postValue(location); }
    public void postStatusMessage(@NonNull String message) { statusMessage.postValue(message); }
    public void clearStatusMessage() { statusMessage.setValue(null); }

    public void insertLocation(@NonNull LocationEntity location) { repository.insert(location); }
    public void clearHistory() { repository.deleteAll(); }
    public void deleteLocation(@NonNull LocationEntity location) { repository.delete(location); }

    public boolean isCurrentlyTracking() {
        Boolean val = isTracking.getValue();
        return val != null && val;
    }

    public long getCurrentIntervalMsValue() {
        Long val = currentIntervalMs.getValue();
        return val != null ? val : Constants.DEFAULT_INTERVAL_MS;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
    }
}
