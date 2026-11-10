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

            if (item.getType() == LibraryItem.Type.LIKED_SONGS) {
                binding.ivLibraryThumb.setImageResource(R.drawable.ic_favorite_filled);
                binding.ivLibraryThumb.setColorFilter(itemView.getContext().getColor(R.color.spotify_green));
                binding.ivLibraryThumb.setBackgroundColor(0xFF282828);
                binding.ivPinIcon.setVisibility(View.VISIBLE);
                binding.ivPinIcon.setImageResource(R.drawable.ic_favorite_filled);
            } else if (item.getType() == LibraryItem.Type.PLAYLIST) {
                binding.ivLibraryThumb.setColorFilter(null);
                Glide.with(itemView.getContext())
                        .load(ApiClient.resolveMediaUrl(item.getCoverUrl()))
                        .placeholder(R.drawable.bg_card_rounded)
                        .error(R.drawable.bg_card_rounded)
                        .into(binding.ivLibraryThumb);

                binding.ivPinIcon.setVisibility(item.isPinned() ? View.VISIBLE : View.GONE);
                if (item.isPinned()) {
                    binding.ivPinIcon.setImageResource(R.drawable.ic_sparkle_ai);
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
            } else if (item.getType() == LibraryItem.Type.ALBUM) {
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
