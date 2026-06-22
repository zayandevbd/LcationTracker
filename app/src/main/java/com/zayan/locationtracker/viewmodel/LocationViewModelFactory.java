package com.zayan.locationtracker.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.zayan.locationtracker.repository.LocationRepository;

public class LocationViewModelFactory implements ViewModelProvider.Factory {

    private final LocationRepository repository;

    public LocationViewModelFactory(@NonNull LocationRepository repository) {
        this.repository = repository;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(LocationViewModel.class)) {
            return (T) new LocationViewModel(repository);
        }
        throw new IllegalArgumentException(
                "LocationViewModelFactory cannot create: " + modelClass.getName());
    }
}
