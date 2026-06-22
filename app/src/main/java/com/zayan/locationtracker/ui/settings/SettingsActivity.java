package com.zayan.locationtracker.ui.settings;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.zayan.locationtracker.R;
import com.zayan.locationtracker.databinding.ActivitySettingsBinding;
import com.zayan.locationtracker.repository.LocationRepository;
import com.zayan.locationtracker.settings.AppSettings;
import com.zayan.locationtracker.utils.Constants;
import com.zayan.locationtracker.utils.ServiceUtils;
import com.zayan.locationtracker.viewmodel.LocationViewModel;
import com.zayan.locationtracker.viewmodel.LocationViewModelFactory;

/**
 * Settings screen — allows the user to change the location update interval.
 *
 * Changes are applied immediately:
 *  1. Saved to SharedPreferences via AppSettings
 *  2. Sent to the running service via ServiceUtils.updateInterval()
 *     (service reconfigures FusedLocationProviderClient in-place)
 *  3. Posted to the ViewModel so MainActivity's status display updates
 *     when the user navigates back
 *
 * No restart of the app or service is required for changes to take effect.
 */
public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;
    private AppSettings appSettings;
    private LocationViewModel viewModel;

    /**
     * Guards against the Spinner firing onItemSelected() immediately when
     * setSelection() is called programmatically during setup.
     * true = suppress the next selection callback, false = process normally.
     */
    private boolean ignoreNextSelection = false;

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbarSettings);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_settings);
        }

        appSettings = AppSettings.getInstance(this);

        setupViewModel();
        setupIntervalSpinner();
        updateCurrentIntervalDisplay();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ─── ViewModel ───────────────────────────────────────────────────────────

    private void setupViewModel() {
        LocationRepository repository = LocationRepository.getInstance(this);
        LocationViewModelFactory factory = new LocationViewModelFactory(repository);
        viewModel = new ViewModelProvider(this, factory).get(LocationViewModel.class);
    }

    // ─── Interval Spinner ────────────────────────────────────────────────────

    private void setupIntervalSpinner() {
        // Build the adapter from our constants — single source of truth.
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                Constants.INTERVAL_LABELS
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerInterval.setAdapter(adapter);

        // Pre-select the currently saved interval.
        // This triggers onItemSelected() immediately — we suppress it with the flag.
        ignoreNextSelection = true;
        binding.spinnerInterval.setSelection(appSettings.getIntervalIndex());

        binding.spinnerInterval.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Suppress the programmatic setSelection() callback on first load.
                if (ignoreNextSelection) {
                    ignoreNextSelection = false;
                    return;
                }

                // Only apply if the user actually selected a different value.
                if (position == appSettings.getIntervalIndex()) {
                    return; // No change — nothing to do.
                }

                long newIntervalMs = Constants.INTERVAL_OPTIONS_MS[position];
                applyNewInterval(newIntervalMs);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Nothing to do — spinner always has a selected item.
            }
        });
    }

    /**
     * Apply a newly selected interval:
     *  1. Persist to SharedPreferences
     *  2. Send to running service (if active) — takes effect immediately
     *  3. Update ViewModel so MainActivity refreshes its interval display
     *  4. Update the "currently active" label on this screen
     *
     * @param intervalMs The newly chosen interval in milliseconds.
     */
    private void applyNewInterval(long intervalMs) {
        // 1. Persist.
        appSettings.setIntervalMs(intervalMs);

        // 2. Update running service — no restart needed.
        // If service isn't running, the saved value will be used on next start.
        ServiceUtils.updateInterval(this, intervalMs);

        // 3. Keep ViewModel in sync — MainActivity observes this LiveData.
        viewModel.setCurrentIntervalMs(intervalMs);

        // 4. Refresh the label below the spinner.
        updateCurrentIntervalDisplay();
    }

    /**
     * Update the descriptive text showing the currently active interval.
     * Called on screen open and after every interval change.
     */
    private void updateCurrentIntervalDisplay() {
        if (binding == null) return;
        String label = appSettings.getIntervalLabel();
        binding.tvCurrentIntervalValue.setText(
                getString(R.string.settings_current_interval, label));
    }
}
