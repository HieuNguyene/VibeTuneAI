package com.example.spotifyclone.presentation.player;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.spotifyclone.R;
import com.example.spotifyclone.data.model.Song;
import com.example.spotifyclone.data.remote.ApiClient;
import com.example.spotifyclone.databinding.ItemSimilarSongBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SimilarSongAdapter extends RecyclerView.Adapter<SimilarSongAdapter.SimilarSongViewHolder> {

    public interface OnSimilarSongClickListener {
        void onPlaySong(Song song, List<Song> list, int position);
    }

    private final List<Song> songs = new ArrayList<>();
    private final OnSimilarSongClickListener listener;

    public SimilarSongAdapter(OnSimilarSongClickListener listener) {
        this.listener = listener;
    }

    public void setSongs(List<Song> newSongs) {
        this.songs.clear();
        if (newSongs != null) {
            this.songs.addAll(newSongs);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SimilarSongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSimilarSongBinding binding = ItemSimilarSongBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new SimilarSongViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SimilarSongViewHolder holder, int position) {
        holder.bind(songs.get(position), position);
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    class SimilarSongViewHolder extends RecyclerView.ViewHolder {
        private final ItemSimilarSongBinding binding;

        public SimilarSongViewHolder(@NonNull ItemSimilarSongBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Song song, int position) {
            binding.tvSimilarTitle.setText(song.getTitle());
            binding.tvSimilarArtist.setText(song.getArtistName());

            Double score = song.getSimilarityScore();
            if (score != null && score > 0) {
                int percent = score > 1.0 ? (int) Math.round(score) : (int) Math.round(score * 100);
                binding.tvSimilarBadge.setVisibility(View.VISIBLE);
                binding.tvSimilarBadge.setText(String.format(Locale.getDefault(), "%d%% tương đồng", percent));
            } else {
                binding.tvSimilarBadge.setVisibility(View.GONE);
            }

            String coverUrl = ApiClient.resolveMediaUrl(song.getCoverImageUrl());
            Glide.with(itemView.getContext())
                    .load(coverUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.bg_card_rounded)
                    .error(R.drawable.bg_card_rounded)
                    .into(binding.ivSimilarArt);

            View.OnClickListener clickAction = v -> {
                if (listener != null) {
                    listener.onPlaySong(song, songs, position);
                }
            };

            itemView.setOnClickListener(clickAction);
            binding.btnSimilarPlay.setOnClickListener(clickAction);
        }
    }
}
