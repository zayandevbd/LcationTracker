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

/**
 * RecyclerView adapter for the location history list.
 *
 * Extends ListAdapter which wraps DiffUtil — new lists submitted via
 * submitList() are diffed on a background thread, and only changed
 * items are rebound. This handles lists of thousands of records
 * without janking the scroll.
 *
 * Each item is a card showing: timestamp, latitude, longitude,
 * accuracy, and provider.
 */
public class LocationHistoryAdapter
        extends ListAdapter<LocationEntity, LocationHistoryAdapter.LocationViewHolder> {

    /**
     * Date formatter shared across all ViewHolder bind calls.
     * Created once here — SimpleDateFormat is expensive to construct
     * and not thread-safe, but onBindViewHolder() always runs on the
     * main thread, so sharing one instance is safe and efficient.
     */
    private final SimpleDateFormat dateFormatter =
            new SimpleDateFormat("dd MMM yyyy  HH:mm:ss", Locale.getDefault());

    // ─── Constructor ─────────────────────────────────────────────────────────

    public LocationHistoryAdapter() {
        super(DIFF_CALLBACK);
    }

    // ─── DiffUtil Callback ────────────────────────────────────────────────────

    /**
     * DiffUtil contract:
     *  areItemsTheSame   — same database row? Compare by primary key (id).
     *  areContentsTheSame — same displayed content? Compare all visible fields.
     *
     * If areItemsTheSame returns true but areContentsTheSame returns false,
     * DiffUtil issues a change payload for that specific item (partial rebind).
     * We don't use payloads here — a full rebind for a changed location is fine.
     */
    private static final DiffUtil.ItemCallback<LocationEntity> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<LocationEntity>() {

                @Override
                public boolean areItemsTheSame(@NonNull LocationEntity oldItem,
                                               @NonNull LocationEntity newItem) {
                    // Same database row = same id.
                    return oldItem.getId() == newItem.getId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull LocationEntity oldItem,
                                                  @NonNull LocationEntity newItem) {
                    // Location records are immutable once written — if the id matches,
                    // the content is identical. Comparing all fields is defensive.
                    return oldItem.getId() == newItem.getId()
                            && Double.compare(oldItem.getLatitude(), newItem.getLatitude()) == 0
                            && Double.compare(oldItem.getLongitude(), newItem.getLongitude()) == 0
                            && Float.compare(oldItem.getAccuracy(), newItem.getAccuracy()) == 0
                            && oldItem.getTimestamp() == newItem.getTimestamp();
                }
            };

    // ─── ViewHolder Creation ──────────────────────────────────────────────────

    @NonNull
    @Override
    public LocationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate via ViewBinding — no R.layout reference needed,
        // type-safe access to all views in item_location.xml.
        ItemLocationBinding itemBinding = ItemLocationBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new LocationViewHolder(itemBinding);
    }

    // ─── Binding ──────────────────────────────────────────────────────────────

    @Override
    public void onBindViewHolder(@NonNull LocationViewHolder holder, int position) {
        holder.bind(getItem(position), dateFormatter);
    }

    // ─── ViewHolder ───────────────────────────────────────────────────────────

    /**
     * Holds references to all views in a single location history card.
     * ViewBinding guarantees these are never null and match the XML exactly.
     */
    static class LocationViewHolder extends RecyclerView.ViewHolder {

        private final ItemLocationBinding binding;

        LocationViewHolder(@NonNull ItemLocationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /**
         * Bind a LocationEntity to this card's views.
         *
         * Called by onBindViewHolder() for every visible item and whenever
         * a visible item's data changes (DiffUtil change notification).
         *
         * @param entity    The location record to display.
         * @param formatter Shared date formatter — do not create a new instance here.
         */
        void bind(@NonNull LocationEntity entity,
                  @NonNull SimpleDateFormat formatter) {

            // Timestamp — formatted as "21 Jun 2026  14:32:01"
            binding.tvItemTimestamp.setText(
                    formatter.format(new Date(entity.getTimestamp())));

            // Coordinates — 6 decimal places (~0.1m precision at equator)
            binding.tvItemLatitude.setText(
                    String.format(Locale.getDefault(),
                            "Lat: %.6f°", entity.getLatitude()));

            binding.tvItemLongitude.setText(
                    String.format(Locale.getDefault(),
                            "Lon: %.6f°", entity.getLongitude()));

            // Accuracy — one decimal is sufficient; GPS doesn't provide sub-cm precision
            if (entity.getAccuracy() >= 0) {
                binding.tvItemAccuracy.setText(
                        String.format(Locale.getDefault(),
                                "Accuracy: ±%.1f m", entity.getAccuracy()));
            } else {
                // Negative accuracy means unavailable from this provider.
                binding.tvItemAccuracy.setText("Accuracy: N/A");
            }

            // Provider — capitalise first letter for display ("Fused", "Gps", "Network")
            String provider = entity.getProvider();
            if (provider != null && !provider.isEmpty()) {
                String displayProvider = provider.substring(0, 1).toUpperCase(Locale.getDefault())
                        + provider.substring(1);
                binding.tvItemProvider.setText("Via: " + displayProvider);
            } else {
                binding.tvItemProvider.setText("Via: Unknown");
            }
        }
    }
}
