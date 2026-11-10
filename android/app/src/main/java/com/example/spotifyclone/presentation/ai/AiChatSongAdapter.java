package com.example.spotifyclone.presentation.ai;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.spotifyclone.R;
import com.example.spotifyclone.data.model.Song;
import com.example.spotifyclone.data.remote.ApiClient;

import java.util.ArrayList;
import java.util.List;

public class AiChatSongAdapter extends RecyclerView.Adapter<AiChatSongAdapter.SongViewHolder> {

    public interface OnChatSongClickListener {
        void onPlaySong(Song song, List<Song> songList, int position);
    }

    private final List<Song> songs = new ArrayList<>();
    private final OnChatSongClickListener listener;

    public AiChatSongAdapter(List<Song> songList, OnChatSongClickListener listener) {
        if (songList != null) {
            this.songs.addAll(songList);
        }
        this.listener = listener;
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_song, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        Song song = songs.get(position);
        holder.bind(song, position);
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    class SongViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivThumb;
        private final TextView tvTitle;
        private final TextView tvArtist;
        private final ImageButton btnPlay;

        public SongViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumb = itemView.findViewById(R.id.ivSongThumb);
            tvTitle = itemView.findViewById(R.id.tvSongTitle);
            tvArtist = itemView.findViewById(R.id.tvSongArtist);
            btnPlay = itemView.findViewById(R.id.btnPlaySong);
        }

        public void bind(Song song, int position) {
            tvTitle.setText(song.getTitle());
            tvArtist.setText(song.getArtistName());

            String coverUrl = ApiClient.resolveMediaUrl(song.getCoverImageUrl());
            Glide.with(itemView.getContext())
                    .load(coverUrl)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.bg_card_rounded)
                    .error(R.drawable.bg_card_rounded)
                    .into(ivThumb);

            View.OnClickListener playAction = v -> {
                if (listener != null) {
                    listener.onPlaySong(song, songs, position);
                }
            };

            itemView.setOnClickListener(playAction);
            btnPlay.setOnClickListener(playAction);
        }
    }
}
