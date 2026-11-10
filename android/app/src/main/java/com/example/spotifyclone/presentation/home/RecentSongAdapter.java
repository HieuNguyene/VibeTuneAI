package com.example.spotifyclone.presentation.home;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.spotifyclone.R;
import com.example.spotifyclone.data.model.Song;
import com.example.spotifyclone.data.remote.ApiClient;
import com.example.spotifyclone.databinding.ItemRecentCardBinding;

import java.util.ArrayList;
import java.util.List;

public class RecentSongAdapter extends RecyclerView.Adapter<RecentSongAdapter.RecentViewHolder> {

    public interface OnRecentSongClickListener {
        void onSongClick(Song song);
    }

    private List<Song> songList = new ArrayList<>();
    private final OnRecentSongClickListener listener;

    public RecentSongAdapter(OnRecentSongClickListener listener) {
        this.listener = listener;
    }

    public void setSongs(List<Song> songs) {
        this.songList = songs != null ? songs : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemRecentCardBinding binding = ItemRecentCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new RecentViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecentViewHolder holder, int position) {
        holder.bind(songList.get(position));
    }

    @Override
    public int getItemCount() {
        return Math.min(songList.size(), 6);
    }

    class RecentViewHolder extends RecyclerView.ViewHolder {
        private final ItemRecentCardBinding binding;

        public RecentViewHolder(@NonNull ItemRecentCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Song song) {
            binding.tvRecentTitle.setText(song.getTitle());

            String imageUrl = ApiClient.resolveMediaUrl(song.getCoverImageUrl());
            Glide.with(itemView.getContext())
                    .load(imageUrl)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.bg_card_rounded)
                    .error(R.drawable.bg_card_rounded)
                    .into(binding.ivRecentArt);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSongClick(song);
                }
            });
        }
    }
}
