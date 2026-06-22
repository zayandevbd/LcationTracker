package com.zayan.locationtracker.ui.history;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.zayan.locationtracker.database.entity.LocationEntity;
import com.zayan.locationtracker.databinding.ItemLocationBinding;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LocationHistoryAdapter
        extends ListAdapter<LocationEntity, LocationHistoryAdapter.LocationViewHolder> {

    private final SimpleDateFormat dateFormatter =
            new SimpleDateFormat("dd MMM yyyy  HH:mm:ss", Locale.getDefault());

    public LocationHistoryAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<LocationEntity> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<LocationEntity>() {
                @Override
                public boolean areItemsTheSame(@NonNull LocationEntity oldItem,
                                               @NonNull LocationEntity newItem) {
                    return oldItem.getId() == newItem.getId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull LocationEntity oldItem,
                                                  @NonNull LocationEntity newItem) {
                    // records are immutable once written — id match implies content match
                    return oldItem.getId() == newItem.getId()
                            && Double.compare(oldItem.getLatitude(), newItem.getLatitude()) == 0
                            && Double.compare(oldItem.getLongitude(), newItem.getLongitude()) == 0
                            && Float.compare(oldItem.getAccuracy(), newItem.getAccuracy()) == 0
                            && oldItem.getTimestamp() == newItem.getTimestamp();
                }
            };

    @NonNull
    @Override
    public LocationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLocationBinding itemBinding = ItemLocationBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new LocationViewHolder(itemBinding);
    }

    @Override
    public void onBindViewHolder(@NonNull LocationViewHolder holder, int position) {
        holder.bind(getItem(position), dateFormatter);
    }

    static class LocationViewHolder extends RecyclerView.ViewHolder {

        private final ItemLocationBinding binding;

        LocationViewHolder(@NonNull ItemLocationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull LocationEntity entity, @NonNull SimpleDateFormat formatter) {
            binding.tvItemTimestamp.setText(formatter.format(new Date(entity.getTimestamp())));

            binding.tvItemLatitude.setText(
                    String.format(Locale.getDefault(), "Lat: %.6f°", entity.getLatitude()));
            binding.tvItemLongitude.setText(
                    String.format(Locale.getDefault(), "Lon: %.6f°", entity.getLongitude()));

            if (entity.getAccuracy() >= 0) {
                binding.tvItemAccuracy.setText(
                        String.format(Locale.getDefault(), "Accuracy: ±%.1f m", entity.getAccuracy()));
            } else {
                binding.tvItemAccuracy.setText("Accuracy: N/A");
            }

            String provider = entity.getProvider();
            if (provider != null && !provider.isEmpty()) {
                String display = provider.substring(0, 1).toUpperCase(Locale.getDefault())
                        + provider.substring(1);
                binding.tvItemProvider.setText("Via: " + display);
            } else {
                binding.tvItemProvider.setText("Via: Unknown");
            }
        }
    }
}
