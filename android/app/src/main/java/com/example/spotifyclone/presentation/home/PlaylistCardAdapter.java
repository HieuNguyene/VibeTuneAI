package com.example.spotifyclone.presentation.home;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.example.spotifyclone.R;
import com.example.spotifyclone.data.model.Playlist;
import com.example.spotifyclone.data.remote.ApiClient;
import com.example.spotifyclone.databinding.ItemSongCardSquareBinding;

import java.util.ArrayList;
import java.util.List;

public class PlaylistCardAdapter extends RecyclerView.Adapter<PlaylistCardAdapter.PlaylistViewHolder> {

    public interface OnPlaylistClickListener {
        void onPlaylistClick(Playlist playlist);
    }

    private List<Playlist> playlistList = new ArrayList<>();
    private final OnPlaylistClickListener listener;

    public PlaylistCardAdapter(OnPlaylistClickListener listener) {
        this.listener = listener;
    }

    public void setPlaylists(List<Playlist> playlists) {
        this.playlistList = playlists != null ? playlists : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSongCardSquareBinding binding = ItemSongCardSquareBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new PlaylistViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistViewHolder holder, int position) {
        holder.bind(playlistList.get(position));
    }

    @Override
    public int getItemCount() {
        return playlistList.size();
    }

    class PlaylistViewHolder extends RecyclerView.ViewHolder {
        private final ItemSongCardSquareBinding binding;

        public PlaylistViewHolder(@NonNull ItemSongCardSquareBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Playlist playlist) {
            binding.tvCardTitle.setText(playlist.getTitle());
            String desc = playlist.getDescription();
            if (desc == null || desc.isEmpty()) {
                desc = playlist.getTotalTracks() + " bài hát";
            }
            binding.tvCardSubtitle.setText(desc);

            String imageUrl = ApiClient.resolveMediaUrl(playlist.getCoverImageUrl());
            Glide.with(itemView.getContext())
                    .load(imageUrl)
                    .transform(new RoundedCorners(16))
                    .placeholder(R.drawable.bg_card_rounded)
                    .error(R.drawable.bg_card_rounded)
                    .into(binding.ivCoverArt);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPlaylistClick(playlist);
                }
            });
        }
    }
}
