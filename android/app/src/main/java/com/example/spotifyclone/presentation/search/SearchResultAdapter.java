package com.example.spotifyclone.presentation.search;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.spotifyclone.R;
import com.example.spotifyclone.data.model.ApiResponse;
import com.example.spotifyclone.data.model.Song;
import com.example.spotifyclone.data.model.ToggleFavoriteData;
import com.example.spotifyclone.data.remote.ApiClient;
import com.example.spotifyclone.databinding.ItemSongListRowBinding;
import com.example.spotifyclone.player.MusicPlayerManager;
import com.example.spotifyclone.presentation.player.SongOptionsBottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.SongViewHolder> {

    public interface OnSearchSongClickListener {
        void onSongClick(Song song);
    }

    private final List<Song> songList = new ArrayList<>();
    private final OnSearchSongClickListener listener;

    public SearchResultAdapter(OnSearchSongClickListener listener) {
        this.listener = listener;
    }

    public void setSongs(List<Song> songs) {
        this.songList.clear();
        if (songs != null) {
            this.songList.addAll(songs);
        }
        notifyDataSetChanged();
    }

    public List<Song> getSongs() {
        return songList;
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSongListRowBinding binding = ItemSongListRowBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
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
        private final ItemSongListRowBinding binding;

        public SongViewHolder(@NonNull ItemSongListRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Song song) {
            binding.tvSongTitle.setText(song.getTitle());
            binding.tvSongArtist.setText(song.getArtistName());

            Glide.with(binding.ivSongThumb.getContext())
                    .load(ApiClient.resolveMediaUrl(song.getCoverImageUrl()))
                    .placeholder(R.drawable.bg_card_rounded)
                    .error(R.drawable.bg_card_rounded)
                    .into(binding.ivSongThumb);

            updateFavoriteUi(song.isLiked());

            binding.btnSongFavorite.setOnClickListener(v -> {
                if (song.getId() == null || song.getId().isEmpty()) return;

                boolean nextLiked = !song.isLiked();
                song.setLiked(nextLiked);
                updateFavoriteUi(nextLiked);

                binding.btnSongFavorite.animate()
                        .scaleX(1.3f).scaleY(1.3f)
                        .setDuration(100)
                        .withEndAction(() -> binding.btnSongFavorite.animate().scaleX(1f).scaleY(1f).setDuration(100).start())
                        .start();

                ApiClient.getApiService().toggleFavorite(song.getId()).enqueue(new Callback<ApiResponse<ToggleFavoriteData>>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse<ToggleFavoriteData>> call, @NonNull Response<ApiResponse<ToggleFavoriteData>> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                            boolean serverLiked = response.body().getData().isLiked();
                            song.setLiked(serverLiked);
                            updateFavoriteUi(serverLiked);
                            if (song.getId().equals(MusicPlayerManager.getInstance().getCurrentSongId())) {
                                MusicPlayerManager.getInstance().setCurrentSongLiked(serverLiked);
                            }
                        } else {
                            song.setLiked(!nextLiked);
                            updateFavoriteUi(!nextLiked);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse<ToggleFavoriteData>> call, @NonNull Throwable t) {
                        song.setLiked(!nextLiked);
                        updateFavoriteUi(!nextLiked);
                    }
                });
            });

            binding.btnSongMore.setOnClickListener(v -> {
                SongOptionsBottomSheetDialog.show(itemView.getContext(), song, isLiked -> {
                    updateFavoriteUi(isLiked);
                });
            });

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSongClick(song);
                }
            });
        }

        private void updateFavoriteUi(boolean liked) {
            binding.btnSongFavorite.setImageResource(
                    liked ? R.drawable.ic_favorite_filled : R.drawable.ic_favorite_border
            );
            int color = liked ?
                    itemView.getContext().getColor(R.color.spotify_green) :
                    itemView.getContext().getColor(R.color.text_secondary);
            binding.btnSongFavorite.setColorFilter(color);
        }
    }
}
