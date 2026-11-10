package com.example.spotifyclone.presentation.home;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.example.spotifyclone.R;
import com.example.spotifyclone.data.model.Song;
import com.example.spotifyclone.data.remote.ApiClient;
import com.example.spotifyclone.databinding.ItemSongCardSquareBinding;

import java.util.ArrayList;
import java.util.List;

public class SongCardAdapter extends RecyclerView.Adapter<SongCardAdapter.SongViewHolder> {

    public interface OnSongClickListener {
        void onSongClick(Song song);
    }

    private List<Song> songList = new ArrayList<>();
    private final OnSongClickListener listener;

    public SongCardAdapter(OnSongClickListener listener) {
        this.listener = listener;
    }

    public void setSongs(List<Song> songs) {
        this.songList = songs != null ? songs : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSongCardSquareBinding binding = ItemSongCardSquareBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new SongViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        holder.bind(songList.get(position));
    }

    @Override
    public int getItemCount() {
        return songList.size();
    }

    class SongViewHolder extends RecyclerView.ViewHolder {
        private final ItemSongCardSquareBinding binding;

        public SongViewHolder(@NonNull ItemSongCardSquareBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Song song) {
            binding.tvCardTitle.setText(song.getTitle());
            binding.tvCardSubtitle.setText(song.getArtistName());

            String imageUrl = ApiClient.resolveMediaUrl(song.getCoverImageUrl());
            Glide.with(itemView.getContext())
                    .load(imageUrl)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .transform(new RoundedCorners(16))
                    .placeholder(R.drawable.bg_card_rounded)
                    .error(R.drawable.bg_card_rounded)
                    .into(binding.ivCoverArt);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSongClick(song);
                }
            });
        }
    }
}
