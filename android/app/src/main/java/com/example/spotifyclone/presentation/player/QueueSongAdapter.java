package com.example.spotifyclone.presentation.player;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.spotifyclone.R;
import com.example.spotifyclone.data.model.Song;
import com.example.spotifyclone.data.remote.ApiClient;
import com.example.spotifyclone.databinding.ItemQueueTrackBinding;

import java.util.ArrayList;
import java.util.List;

public class QueueSongAdapter extends RecyclerView.Adapter<QueueSongAdapter.QueueViewHolder> {

    public interface OnQueueItemClickListener {
        void onQueueSongClick(Song song, int position);
    }

    private final List<Song> songList = new ArrayList<>();
    private int currentPlayingIndex = -1;
    private final OnQueueItemClickListener listener;

    public QueueSongAdapter(OnQueueItemClickListener listener) {
        this.listener = listener;
    }

    public void setQueue(List<Song> songs, int activeIndex) {
        this.songList.clear();
        if (songs != null) {
            this.songList.addAll(songs);
        }
        this.currentPlayingIndex = activeIndex;
        notifyDataSetChanged();
    }

    public void setActiveIndex(int activeIndex) {
        this.currentPlayingIndex = activeIndex;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public QueueViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemQueueTrackBinding binding = ItemQueueTrackBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new QueueViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull QueueViewHolder holder, int position) {
        holder.bind(songList.get(position), position == currentPlayingIndex);
    }

    @Override
    public int getItemCount() {
        return songList.size();
    }

    class QueueViewHolder extends RecyclerView.ViewHolder {
        private final ItemQueueTrackBinding binding;

        public QueueViewHolder(@NonNull ItemQueueTrackBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Song song, boolean isCurrentPlaying) {
            Context context = itemView.getContext();
            binding.tvQueueTitle.setText(song.getTitle());
            binding.tvQueueArtist.setText(song.getArtistName());

            if (isCurrentPlaying) {
                binding.tvQueueTitle.setTextColor(ContextCompat.getColor(context, R.color.spotify_green));
                binding.ivPlayingIndicator.setVisibility(View.VISIBLE);
            } else {
                binding.tvQueueTitle.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
                binding.ivPlayingIndicator.setVisibility(View.GONE);
            }

            String coverUrl = ApiClient.resolveMediaUrl(song.getCoverImageUrl());
            Glide.with(context)
                    .load(coverUrl)
                    .placeholder(R.drawable.bg_card_rounded)
                    .error(R.drawable.bg_card_rounded)
                    .into(binding.ivQueueArt);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onQueueSongClick(song, pos);
                }
            });
        }
    }
}
