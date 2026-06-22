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

public class HistoryActivity extends AppCompatActivity {

    private ActivityHistoryBinding binding;
    private LocationViewModel viewModel;
    private LocationHistoryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_history, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        if (item.getItemId() == R.id.action_clear_history) {
            showClearHistoryConfirmDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupViewModel() {
        LocationRepository repository = LocationRepository.getInstance(this);
        LocationViewModelFactory factory = new LocationViewModelFactory(repository);
        viewModel = new ViewModelProvider(this, factory).get(LocationViewModel.class);
    }

    private void setupRecyclerView() {
        adapter = new LocationHistoryAdapter();
        binding.recyclerHistory.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerHistory.setAdapter(adapter);
        binding.recyclerHistory.setHasFixedSize(false);
        binding.recyclerHistory.setBackground(null);
    }

    private void observeLocationHistory() {
        viewModel.getLocationHistory().observe(this, locations -> {
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

    private void showEmptyState() {
        binding.recyclerHistory.setVisibility(View.GONE);
        binding.layoutEmptyState.setVisibility(View.VISIBLE);
    }

    private void showList() {
        binding.recyclerHistory.setVisibility(View.VISIBLE);
        binding.layoutEmptyState.setVisibility(View.GONE);
    }

    private void showClearHistoryConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_clear_history_title)
                .setMessage(R.string.dialog_clear_history_message)
                .setPositiveButton(R.string.dialog_clear_confirm, (dialog, which) ->
                        viewModel.clearHistory())
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }
}
