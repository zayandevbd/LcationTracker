package com.zayan.locationtracker.ui.history;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.zayan.locationtracker.R;
import com.zayan.locationtracker.databinding.ActivityHistoryBinding;
import com.zayan.locationtracker.repository.LocationRepository;
import com.zayan.locationtracker.viewmodel.LocationViewModel;
import com.zayan.locationtracker.viewmodel.LocationViewModelFactory;

import java.util.Collections;

/**
 * Displays the full location tracking history in a RecyclerView.
 *
 * Data comes from Room via LiveData — the list updates automatically
 * whenever the tracking service inserts a new location record.
 * No manual refresh needed.
 *
 * Features:
 *  - Newest entries shown first (enforced at the SQL level in LocationDao)
 *  - Empty state view when no records exist
 *  - Clear All option in the overflow menu with confirmation dialog
 *  - Up/back navigation to MainActivity (declared in manifest via parentActivityName)
 */
public class HistoryActivity extends AppCompatActivity {

    private ActivityHistoryBinding binding;
    private LocationViewModel viewModel;
    private LocationHistoryAdapter adapter;

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Toolbar with back navigation.
        setSupportActionBar(binding.toolbarHistory);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_history);
        }

        setupViewModel();
        setupRecyclerView();
        observeLocationHistory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    // ─── Options Menu (Clear All) ─────────────────────────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_history, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // Handles the toolbar back arrow.
            onBackPressed();
            return true;
        }
        if (item.getItemId() == R.id.action_clear_history) {
            showClearHistoryConfirmDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ─── Setup ────────────────────────────────────────────────────────────────

    private void setupViewModel() {
        LocationRepository repository = LocationRepository.getInstance(this);
        LocationViewModelFactory factory = new LocationViewModelFactory(repository);
        viewModel = new ViewModelProvider(this, factory).get(LocationViewModel.class);
    }

    private void setupRecyclerView() {
        adapter = new LocationHistoryAdapter();

        binding.recyclerHistory.setLayoutManager(
                new LinearLayoutManager(this));
        binding.recyclerHistory.setAdapter(adapter);

        // Improves scroll performance — our items all have the same height.
        binding.recyclerHistory.setHasFixedSize(false);

        // Reduce overdraw — the RecyclerView background matches the window background.
        binding.recyclerHistory.setBackground(null);
    }

    // ─── LiveData Observation ─────────────────────────────────────────────────

    private void observeLocationHistory() {
        viewModel.getLocationHistory().observe(this, locations -> {
            // Hide the loading spinner — data has arrived (even if empty).
            binding.progressLoading.setVisibility(View.GONE);

            if (locations == null || locations.isEmpty()) {
                showEmptyState();
                adapter.submitList(Collections.emptyList());
            } else {
                showList();
                adapter.submitList(locations);
            }
        });
    }

    // ─── State Views ──────────────────────────────────────────────────────────

    private void showEmptyState() {
        binding.recyclerHistory.setVisibility(View.GONE);
        binding.layoutEmptyState.setVisibility(View.VISIBLE);
    }

    private void showList() {
        binding.recyclerHistory.setVisibility(View.VISIBLE);
        binding.layoutEmptyState.setVisibility(View.GONE);
    }

    // ─── Clear History ────────────────────────────────────────────────────────

    /**
     * Show a confirmation dialog before deleting all records.
     * Destructive actions always require explicit user confirmation.
     */
    private void showClearHistoryConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_clear_history_title)
                .setMessage(R.string.dialog_clear_history_message)
                .setPositiveButton(R.string.dialog_clear_confirm, (dialog, which) -> {
                    viewModel.clearHistory();
                    // LiveData auto-updates the RecyclerView after deletion.
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }
}
