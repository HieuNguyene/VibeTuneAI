package com.example.spotifyclone.presentation.home;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.example.spotifyclone.R;
import com.example.spotifyclone.data.model.Album;
import com.example.spotifyclone.data.remote.ApiClient;
import com.example.spotifyclone.databinding.ItemAlbumCardBinding;

import java.util.ArrayList;
import java.util.List;

public class AlbumCardAdapter extends RecyclerView.Adapter<AlbumCardAdapter.AlbumViewHolder> {

    public interface OnAlbumClickListener {
        void onAlbumClick(Album album);
    }

    private List<Album> albumList = new ArrayList<>();
    private final OnAlbumClickListener listener;

    public AlbumCardAdapter(OnAlbumClickListener listener) {
        this.listener = listener;
    }

    public void setAlbums(List<Album> albums) {
        this.albumList = albums != null ? albums : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AlbumViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAlbumCardBinding binding = ItemAlbumCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new AlbumViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull AlbumViewHolder holder, int position) {
        holder.bind(albumList.get(position));
    }

    @Override
    public int getItemCount() {
        return albumList.size();
    }

    class AlbumViewHolder extends RecyclerView.ViewHolder {
        private final ItemAlbumCardBinding binding;

        public AlbumViewHolder(@NonNull ItemAlbumCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Album album) {
            binding.tvAlbumTitle.setText(album.getTitle());

            String subtitle = album.getArtistName();
            if (album.getReleaseYear() > 0) {
                subtitle += " • " + album.getReleaseYear();
            }
            binding.tvAlbumSubtitle.setText(subtitle);

            int tracks = album.getTotalTracks();
            binding.tvAlbumTrackBadge.setText(tracks > 0 ? (tracks + " bài") : "Album");

            String imageUrl = ApiClient.resolveMediaUrl(album.getCoverImageUrl());
            Glide.with(itemView.getContext())
                    .load(imageUrl)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .transform(new RoundedCorners(16))
                    .placeholder(R.drawable.bg_card_rounded)
                    .error(R.drawable.bg_card_rounded)
                    .into(binding.ivAlbumCover);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAlbumClick(album);
                }
            });
        }
    }
}
