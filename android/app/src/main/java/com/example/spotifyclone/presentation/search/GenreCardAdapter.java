package com.example.spotifyclone.presentation.search;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.spotifyclone.R;
import com.example.spotifyclone.data.model.Genre;
import com.example.spotifyclone.databinding.ItemGenreCardBinding;

import java.util.ArrayList;
import java.util.List;

public class GenreCardAdapter extends RecyclerView.Adapter<GenreCardAdapter.GenreViewHolder> {

    public interface OnGenreClickListener {
        void onGenreClick(Genre genre);
    }

    private final List<Genre> genreList = new ArrayList<>();
    private final OnGenreClickListener listener;

    public GenreCardAdapter(OnGenreClickListener listener) {
        this.listener = listener;
    }

    public void setGenres(List<Genre> genres) {
        this.genreList.clear();
        if (genres != null) {
            this.genreList.addAll(genres);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public GenreViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemGenreCardBinding binding = ItemGenreCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new GenreViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull GenreViewHolder holder, int position) {
        holder.bind(genreList.get(position));
    }

    @Override
    public int getItemCount() {
        return genreList.size();
    }

    class GenreViewHolder extends RecyclerView.ViewHolder {
        private final ItemGenreCardBinding binding;

        public GenreViewHolder(@NonNull ItemGenreCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Genre genre) {
            binding.tvGenreTitle.setText(genre.getName());

            if (genre.getColorCode() != null && !genre.getColorCode().isEmpty()) {
                try {
                    binding.getRoot().setCardBackgroundColor(Color.parseColor(genre.getColorCode()));
                } catch (Exception ignored) {}
            }

            if (genre.getIconUrl() != null && !genre.getIconUrl().isEmpty()) {
                Glide.with(binding.ivGenreThumbnail.getContext())
                        .load(genre.getIconUrl())
                        .placeholder(R.drawable.bg_card_rounded)
                        .error(R.drawable.bg_card_rounded)
                        .into(binding.ivGenreThumbnail);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onGenreClick(genre);
                }
            });
        }
    }
}
