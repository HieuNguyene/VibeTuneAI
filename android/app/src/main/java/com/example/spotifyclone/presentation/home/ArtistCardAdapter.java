package com.example.spotifyclone.presentation.home;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.spotifyclone.R;
import com.example.spotifyclone.data.model.Artist;
import com.example.spotifyclone.data.remote.ApiClient;
import com.example.spotifyclone.databinding.ItemSongCardSquareBinding;

import java.util.ArrayList;
import java.util.List;

public class ArtistCardAdapter extends RecyclerView.Adapter<ArtistCardAdapter.ArtistViewHolder> {

    public interface OnArtistClickListener {
        void onArtistClick(Artist artist);
    }

    private List<Artist> artistList = new ArrayList<>();
    private final OnArtistClickListener listener;

    public ArtistCardAdapter() {
        this(null);
    }

    public ArtistCardAdapter(OnArtistClickListener listener) {
        this.listener = listener;
    }

    public void setArtists(List<Artist> artists) {
        this.artistList = artists != null ? artists : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ArtistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSongCardSquareBinding binding = ItemSongCardSquareBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new ArtistViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ArtistViewHolder holder, int position) {
        holder.bind(artistList.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return artistList.size();
    }

    static class ArtistViewHolder extends RecyclerView.ViewHolder {
        private final ItemSongCardSquareBinding binding;

        public ArtistViewHolder(@NonNull ItemSongCardSquareBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Artist artist, OnArtistClickListener listener) {
            binding.tvCardTitle.setText(artist.getName());
            binding.tvCardSubtitle.setText("Nghệ sĩ");

            String avatar = ApiClient.resolveMediaUrl(artist.getAvatarUrl());
            Glide.with(itemView.getContext())
                    .load(avatar)
                    .circleCrop()
                    .placeholder(R.drawable.bg_card_rounded)
                    .error(R.drawable.bg_card_rounded)
                    .into(binding.ivCoverArt);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onArtistClick(artist);
                }
            });
        }
    }
}
