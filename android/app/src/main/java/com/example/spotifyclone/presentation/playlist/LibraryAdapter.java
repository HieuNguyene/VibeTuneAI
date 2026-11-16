package com.example.spotifyclone.presentation.playlist;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.spotifyclone.R;
import com.example.spotifyclone.data.remote.ApiClient;
import com.example.spotifyclone.databinding.ItemLibraryRowBinding;

import java.util.ArrayList;
import java.util.List;

public class LibraryAdapter extends RecyclerView.Adapter<LibraryAdapter.LibraryViewHolder> {

    public interface OnLibraryItemClickListener {
        void onLibraryItemClick(LibraryItem item);
    }

    private final List<LibraryItem> items = new ArrayList<>();
    private final OnLibraryItemClickListener listener;

    public LibraryAdapter(OnLibraryItemClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<LibraryItem> newItems) {
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LibraryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLibraryRowBinding binding = ItemLibraryRowBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new LibraryViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull LibraryViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class LibraryViewHolder extends RecyclerView.ViewHolder {
        private final ItemLibraryRowBinding binding;

        public LibraryViewHolder(@NonNull ItemLibraryRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(LibraryItem item) {
            binding.tvLibraryTitle.setText(item.getTitle());
            binding.tvLibrarySubtitle.setText(item.getSubtitle());

            boolean isLocked = item.isLocked();
            if (isLocked) {
                binding.ivLibraryThumb.setAlpha(0.55f);
                binding.tvLibraryTitle.setAlpha(0.55f);
                binding.tvLibrarySubtitle.setAlpha(0.70f);
            } else {
                binding.ivLibraryThumb.setAlpha(1.0f);
                binding.tvLibraryTitle.setAlpha(1.0f);
                binding.tvLibrarySubtitle.setAlpha(1.0f);
            }

            if (item.getType() == LibraryItem.Type.LIKED_SONGS) {
                binding.ivLibraryThumb.setImageResource(R.drawable.ic_favorite_filled);
                binding.ivLibraryThumb.setColorFilter(itemView.getContext().getColor(R.color.spotify_green));
                binding.ivLibraryThumb.setBackgroundColor(
                        androidx.core.content.ContextCompat.getColor(itemView.getContext(), R.color.spotify_hover));
                binding.ivPinIcon.setVisibility(View.VISIBLE);
                binding.ivPinIcon.setImageResource(R.drawable.ic_favorite_filled);
                binding.ivPinIcon.setColorFilter(itemView.getContext().getColor(R.color.spotify_green));
            } else if (item.getType() == LibraryItem.Type.PLAYLIST) {
                binding.ivLibraryThumb.setColorFilter(null);
                Glide.with(itemView.getContext())
                        .load(ApiClient.resolveMediaUrl(item.getCoverUrl()))
                        .placeholder(R.drawable.bg_card_rounded)
                        .error(R.drawable.bg_card_rounded)
                        .into(binding.ivLibraryThumb);

                if (isLocked) {
                    binding.ivPinIcon.setVisibility(View.VISIBLE);
                    binding.ivPinIcon.setImageResource(R.drawable.ic_lock);
                    binding.ivPinIcon.setColorFilter(
                            androidx.core.content.ContextCompat.getColor(itemView.getContext(), R.color.text_secondary));
                } else if (item.isPinned()) {
                    binding.ivPinIcon.setVisibility(View.VISIBLE);
                    binding.ivPinIcon.setImageResource(R.drawable.ic_music_pin);
                    binding.ivPinIcon.setColorFilter(itemView.getContext().getColor(R.color.spotify_green));
                } else {
                    binding.ivPinIcon.setVisibility(View.GONE);
                }
            } else if (item.getType() == LibraryItem.Type.ARTIST) {
                binding.ivLibraryThumb.setColorFilter(null);
                Glide.with(itemView.getContext())
                        .load(ApiClient.resolveMediaUrl(item.getCoverUrl()))
                        .circleCrop()
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person)
                        .into(binding.ivLibraryThumb);
                binding.ivPinIcon.setVisibility(View.GONE);
            } else if (item.getType() == LibraryItem.Type.ALBUM || item.getType() == LibraryItem.Type.SONG) {
                binding.ivLibraryThumb.setColorFilter(null);
                Glide.with(itemView.getContext())
                        .load(ApiClient.resolveMediaUrl(item.getCoverUrl()))
                        .placeholder(R.drawable.bg_card_rounded)
                        .error(R.drawable.bg_card_rounded)
                        .into(binding.ivLibraryThumb);
                binding.ivPinIcon.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onLibraryItemClick(item);
                }
            });
        }
    }
}
