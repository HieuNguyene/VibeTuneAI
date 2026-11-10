package com.example.spotifyclone.presentation.player;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.spotifyclone.R;
import com.example.spotifyclone.data.model.ApiResponse;
import com.example.spotifyclone.data.model.Playlist;
import com.example.spotifyclone.data.model.Song;
import com.example.spotifyclone.data.model.ToggleFavoriteData;
import com.example.spotifyclone.data.remote.ApiClient;
import com.example.spotifyclone.databinding.LayoutBottomSheetAddToPlaylistBinding;
import com.example.spotifyclone.databinding.LayoutBottomSheetSongOptionsBinding;
import com.example.spotifyclone.player.MusicPlayerManager;
import com.example.spotifyclone.presentation.artist.ArtistDetailActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SongOptionsBottomSheetDialog {

    public interface OnFavoriteChangedListener {
        void onFavoriteChanged(boolean isLiked);
    }

    public static void show(Context context, Song song, OnFavoriteChangedListener listener) {
        if (context == null || song == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(context, R.style.Theme_Spotify_BottomSheet);
        LayoutBottomSheetSongOptionsBinding binding = LayoutBottomSheetSongOptionsBinding.inflate(
                LayoutInflater.from(context)
        );
        dialog.setContentView(binding.getRoot());

        // Bind song header
        binding.tvSongTitle.setText(song.getTitle());
        binding.tvSongArtist.setText(song.getArtistName());
        Glide.with(context)
                .load(ApiClient.resolveMediaUrl(song.getCoverImageUrl()))
                .placeholder(R.drawable.bg_card_rounded)
                .error(R.drawable.bg_card_rounded)
                .into(binding.ivSongCover);

        // Setup Favorite state
        updateFavoriteUi(binding, song.isLiked());
        binding.itemSongFavorite.setOnClickListener(v -> {
            if (song.getId() == null || song.getId().isEmpty()) return;
            boolean nextLiked = !song.isLiked();
            song.setLiked(nextLiked);
            updateFavoriteUi(binding, nextLiked);
            if (listener != null) {
                listener.onFavoriteChanged(nextLiked);
            }
            if (song.getId().equals(MusicPlayerManager.getInstance().getCurrentSongId())) {
                MusicPlayerManager.getInstance().setCurrentSongLiked(nextLiked);
            }
            Toast.makeText(context, nextLiked ? "Đã thêm vào bài hát đã thích ❤️" : "Đã xóa khỏi bài hát đã thích", Toast.LENGTH_SHORT).show();

            ApiClient.getApiService().toggleFavorite(song.getId()).enqueue(new Callback<ApiResponse<ToggleFavoriteData>>() {
                @Override
                public void onResponse(@NonNull Call<ApiResponse<ToggleFavoriteData>> call, @NonNull Response<ApiResponse<ToggleFavoriteData>> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                        boolean serverLiked = response.body().getData().isLiked();
                        song.setLiked(serverLiked);
                        updateFavoriteUi(binding, serverLiked);
                        if (listener != null) {
                            listener.onFavoriteChanged(serverLiked);
                        }
                    }
                }

                @Override
                public void onFailure(@NonNull Call<ApiResponse<ToggleFavoriteData>> call, @NonNull Throwable t) {
                    // Fail silently or keep state
                }
            });
        });

        // Setup Add to Playlist
        binding.itemSongAddToPlaylist.setOnClickListener(v -> {
            dialog.dismiss();
            showAddToPlaylistDialog(context, song);
        });

        // Setup Add to Queue
        binding.itemSongAddToQueue.setOnClickListener(v -> {
            MusicPlayerManager.getInstance().addToQueue(song);
            Toast.makeText(context, "Đã thêm \"" + song.getTitle() + "\" vào danh sách chờ", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        // Setup Go to Artist
        binding.itemSongGoToArtist.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(context, ArtistDetailActivity.class);
            if (song.getArtist() != null) {
                intent.putExtra(ArtistDetailActivity.EXTRA_ARTIST_ID, song.getArtist().getId());
                intent.putExtra(ArtistDetailActivity.EXTRA_ARTIST_NAME, song.getArtist().getName());
                intent.putExtra(ArtistDetailActivity.EXTRA_ARTIST_AVATAR, song.getArtist().getAvatarUrl());
                intent.putExtra(ArtistDetailActivity.EXTRA_ARTIST_BIO, song.getArtist().getBio());
                intent.putExtra(ArtistDetailActivity.EXTRA_ARTIST_FOLLOWERS, song.getArtist().getFollowersCount());
            } else {
                intent.putExtra(ArtistDetailActivity.EXTRA_ARTIST_NAME, song.getArtistName());
            }
            context.startActivity(intent);
        });

        // Setup Share
        binding.itemSongShare.setOnClickListener(v -> {
            dialog.dismiss();
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Nghe nhạc trên Vibe Music");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Đang nghe bài hát \"" + song.getTitle() + "\" của " + song.getArtistName() + " trên Vibe Music! 🎵");
            context.startActivity(Intent.createChooser(shareIntent, "Chia sẻ bài hát qua"));
        });

        dialog.show();
    }

    private static void updateFavoriteUi(LayoutBottomSheetSongOptionsBinding binding, boolean isLiked) {
        binding.ivFavoriteIcon.setImageResource(isLiked ? R.drawable.ic_favorite_filled : R.drawable.ic_favorite_border);
        int color = isLiked ?
                binding.getRoot().getContext().getColor(R.color.spotify_green) :
                binding.getRoot().getContext().getColor(R.color.text_primary);
        binding.ivFavoriteIcon.setColorFilter(color);
        binding.tvFavoriteText.setText(isLiked ? "Bỏ thích bài hát này" : "Thêm vào danh sách yêu thích");
    }

    private static void showAddToPlaylistDialog(Context context, Song song) {
        BottomSheetDialog playlistDialog = new BottomSheetDialog(context, R.style.Theme_Spotify_BottomSheet);
        LayoutBottomSheetAddToPlaylistBinding playlistBinding = LayoutBottomSheetAddToPlaylistBinding.inflate(
                LayoutInflater.from(context)
        );
        playlistDialog.setContentView(playlistBinding.getRoot());

        playlistBinding.btnCreateNewPlaylist.setOnClickListener(v -> {
            Toast.makeText(context, "Vào mục Thư viện để tạo Danh sách phát mới", Toast.LENGTH_SHORT).show();
            playlistDialog.dismiss();
        });

        playlistBinding.pbLoadingPlaylists.setVisibility(View.VISIBLE);
        playlistBinding.rvUserPlaylists.setLayoutManager(new LinearLayoutManager(context));

        ApiClient.getApiService().getMyPlaylists().enqueue(new Callback<ApiResponse<List<Playlist>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<Playlist>>> call, @NonNull Response<ApiResponse<List<Playlist>>> response) {
                playlistBinding.pbLoadingPlaylists.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null && !response.body().getData().isEmpty()) {
                    List<Playlist> playlists = response.body().getData();
                    PlaylistPickerAdapter adapter = new PlaylistPickerAdapter(playlists, playlist -> {
                        addSongToPlaylist(context, playlist.getId(), playlist.getTitle(), song.getId());
                        playlistDialog.dismiss();
                    });
                    playlistBinding.rvUserPlaylists.setAdapter(adapter);
                } else {
                    playlistBinding.tvNoPlaylists.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<Playlist>>> call, @NonNull Throwable t) {
                playlistBinding.pbLoadingPlaylists.setVisibility(View.GONE);
                playlistBinding.tvNoPlaylists.setText("Không thể tải danh sách phát: " + t.getMessage());
                playlistBinding.tvNoPlaylists.setVisibility(View.VISIBLE);
            }
        });

        playlistDialog.show();
    }

    private static void addSongToPlaylist(Context context, String playlistId, String playlistTitle, String songId) {
        if (playlistId == null || songId == null) return;
        Map<String, String> body = new HashMap<>();
        body.put("songId", songId);

        ApiClient.getApiService().addTrackToPlaylist(playlistId, body).enqueue(new Callback<ApiResponse<Object>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Object>> call, @NonNull Response<ApiResponse<Object>> response) {
                Toast.makeText(context, "Đã thêm bài hát vào " + playlistTitle, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<Object>> call, @NonNull Throwable t) {
                Toast.makeText(context, "Lỗi thêm vào danh sách phát: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
