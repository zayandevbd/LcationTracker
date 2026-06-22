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

public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;
    private AppSettings appSettings;
    private LocationViewModel viewModel;

    private boolean ignoreNextSelection = false;

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

    private void setupViewModel() {
        LocationRepository repository = LocationRepository.getInstance(this);
        LocationViewModelFactory factory = new LocationViewModelFactory(repository);
        viewModel = new ViewModelProvider(this, factory).get(LocationViewModel.class);
    }

    private void setupIntervalSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                Constants.INTERVAL_LABELS
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerInterval.setAdapter(adapter);

        ignoreNextSelection = true;
        binding.spinnerInterval.setSelection(appSettings.getIntervalIndex());

        binding.spinnerInterval.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (ignoreNextSelection) {
                    ignoreNextSelection = false;
                    return;
                }
                if (position == appSettings.getIntervalIndex()) return;
                applyNewInterval(Constants.INTERVAL_OPTIONS_MS[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void applyNewInterval(long intervalMs) {
        appSettings.setIntervalMs(intervalMs);
        ServiceUtils.updateInterval(this, intervalMs);
        viewModel.setCurrentIntervalMs(intervalMs);
        updateCurrentIntervalDisplay();
    }

    private void updateCurrentIntervalDisplay() {
        if (binding == null) return;
        binding.tvCurrentIntervalValue.setText(
                getString(R.string.settings_current_interval, appSettings.getIntervalLabel()));
    }
}
