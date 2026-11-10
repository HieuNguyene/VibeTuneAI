package com.example.spotifyclone.presentation.player;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.spotifyclone.R;
import com.example.spotifyclone.data.model.Playlist;
import com.example.spotifyclone.data.remote.ApiClient;
import com.example.spotifyclone.databinding.ItemLibraryRowBinding;

import java.util.ArrayList;
import java.util.List;

public class PlaylistPickerAdapter extends RecyclerView.Adapter<PlaylistPickerAdapter.PlaylistViewHolder> {

    public interface OnPlaylistSelectedListener {
        void onPlaylistSelected(Playlist playlist);
    }

    private final List<Playlist> playlists = new ArrayList<>();
    private final OnPlaylistSelectedListener listener;

    public PlaylistPickerAdapter(List<Playlist> list, OnPlaylistSelectedListener listener) {
        if (list != null) {
            this.playlists.addAll(list);
        }
        this.listener = listener;
    }

    @NonNull
    @Override
    public PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLibraryRowBinding binding = ItemLibraryRowBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new PlaylistViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistViewHolder holder, int position) {
        holder.bind(playlists.get(position));
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }

    class PlaylistViewHolder extends RecyclerView.ViewHolder {
        private final ItemLibraryRowBinding binding;

        public PlaylistViewHolder(@NonNull ItemLibraryRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Playlist playlist) {
            binding.tvLibraryTitle.setText(playlist.getTitle());
            binding.tvLibrarySubtitle.setText(playlist.getTotalTracks() + " bài hát");
            binding.ivPinIcon.setVisibility(View.GONE);

            String cover = ApiClient.resolveMediaUrl(playlist.getCoverImageUrl());
            Glide.with(itemView.getContext())
                    .load(cover)
                    .placeholder(R.drawable.bg_card_rounded)
                    .error(R.drawable.bg_card_rounded)
                    .into(binding.ivLibraryThumb);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPlaylistSelected(playlist);
                }
            });
        }
    }
}
