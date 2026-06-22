package com.zayan.locationtracker.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.zayan.locationtracker.repository.LocationRepository;

/**
 * Factory for constructing LocationViewModel with its required dependencies.
 *
 * Android's ViewModelProvider cannot instantiate ViewModels that have
 * constructor parameters — it only knows zero-argument constructors.
 * This factory bridges that gap by holding the dependencies and handing
 * them to the ViewModel when the framework calls create().
 *
 * Usage in an Activity:
 *
 *   LocationRepository repo = LocationRepository.getInstance(this);
 *   LocationViewModelFactory factory = new LocationViewModelFactory(repo);
 *   viewModel = new ViewModelProvider(this, factory).get(LocationViewModel.class);
 *
 * The ViewModelProvider handles lifecycle scoping — it returns the existing
 * ViewModel instance on rotation rather than creating a new one.
 * The factory is only ever called once per ViewModel lifecycle.
 */
public class LocationViewModelFactory implements ViewModelProvider.Factory {

    private final LocationRepository repository;

    /**
     * @param repository The shared repository instance to inject into the ViewModel.
     *                   Should be obtained via LocationRepository.getInstance(context).
     */
    public LocationViewModelFactory(@NonNull LocationRepository repository) {
        this.repository = repository;
    }

    /**
     * Called by ViewModelProvider when a new ViewModel instance is needed.
     *
     * The cast is safe: we check the modelClass before constructing anything.
     * The unchecked cast warning is suppressed because isAssignableFrom() already
     * guarantees type safety — this is the standard pattern for ViewModel factories.
     *
     * @param modelClass The class of the ViewModel to create.
     * @param <T>        The ViewModel type.
     * @return A new instance of the requested ViewModel.
     * @throws IllegalArgumentException if an unsupported ViewModel class is requested.
     *         This catches mistakes early (e.g. passing the wrong factory to the
     *         wrong screen) rather than failing silently.
     */
    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(LocationViewModel.class)) {
            return (T) new LocationViewModel(repository);
        }
        // Fail loudly rather than returning null or a broken instance.
        // This makes misconfiguration obvious immediately during development.
        throw new IllegalArgumentException(
                "LocationViewModelFactory cannot create ViewModel of type: "
                        + modelClass.getName()
                        + ". Expected: " + LocationViewModel.class.getName()
        );
    }
}
