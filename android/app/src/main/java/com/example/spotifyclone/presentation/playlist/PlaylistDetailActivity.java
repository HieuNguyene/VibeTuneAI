package com.example.spotifyclone.presentation.playlist;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.spotifyclone.R;
import com.example.spotifyclone.data.model.Album;
import com.example.spotifyclone.data.model.ApiResponse;
import com.example.spotifyclone.data.model.FavoritesData;
import com.example.spotifyclone.data.model.Playlist;
import com.example.spotifyclone.data.model.Song;
import com.example.spotifyclone.data.remote.ApiClient;
import com.example.spotifyclone.databinding.ActivityPlaylistDetailBinding;
import com.example.spotifyclone.databinding.LayoutBottomSheetPlaylistOptionsBinding;
import com.example.spotifyclone.player.MusicPlayerManager;
import com.example.spotifyclone.presentation.player.PlayerActivity;
import com.example.spotifyclone.presentation.search.SearchResultAdapter;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PlaylistDetailActivity extends AppCompatActivity implements SearchResultAdapter.OnSearchSongClickListener {

    public static final String EXTRA_PLAYLIST_TYPE = "PLAYLIST_TYPE";
    public static final String TYPE_LIKED_SONGS = "LIKED_SONGS";
    public static final String TYPE_CUSTOM_PLAYLIST = "CUSTOM_PLAYLIST";
    public static final String TYPE_ALBUM = "ALBUM";

    public static final String EXTRA_PLAYLIST_ID = "PLAYLIST_ID";
    public static final String EXTRA_PLAYLIST_TITLE = "PLAYLIST_TITLE";
    public static final String EXTRA_PLAYLIST_COVER = "PLAYLIST_COVER";

    private ActivityPlaylistDetailBinding binding;
    private SearchResultAdapter trackAdapter;
    private final List<Song> songList = new ArrayList<>();
    private String playlistType = TYPE_LIKED_SONGS;
    private String playlistId = "";
    private boolean isPlaylistLiked = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlaylistDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Bù trừ an toàn thanh trạng thái cho Toolbar, AppBar và thanh điều hướng đáy cho danh sách bài hát
        com.example.spotifyclone.util.WindowInsetHelper.applyCollapsingToolbarInsets(
                binding.getRoot(),
                binding.appBarLayout,
                binding.toolbarDetail,
                binding.ivPlaylistCover,
                binding.scrollPlaylistContent
        );

        parseIntent();
        setupToolbar();
        setupRecyclerView();
        setupActions();
        loadPlaylistData();
    }

    private void parseIntent() {
        playlistType = getIntent().getStringExtra(EXTRA_PLAYLIST_TYPE);
        if (playlistType == null) playlistType = TYPE_LIKED_SONGS;
        playlistId = getIntent().getStringExtra(EXTRA_PLAYLIST_ID);

        String title = getIntent().getStringExtra(EXTRA_PLAYLIST_TITLE);
        String cover = getIntent().getStringExtra(EXTRA_PLAYLIST_COVER);

        if (TYPE_LIKED_SONGS.equals(playlistType)) {
            binding.tvPlaylistTitle.setText("Bài hát đã thích");
            binding.tvToolbarTitle.setText("Bài hát đã thích");
            binding.tvPlaylistDetails.setText("Đang tải danh sách...");
            binding.ivPlaylistCover.setImageResource(R.drawable.ic_favorite_filled);
            binding.ivPlaylistCover.setColorFilter(getColor(R.color.spotify_green));
        } else {
            if (title != null) {
                binding.tvPlaylistTitle.setText(title);
                binding.tvToolbarTitle.setText(title);
            }
            if (cover != null && !cover.isEmpty()) {
                Glide.with(this)
                        .load(ApiClient.resolveMediaUrl(cover))
                        .placeholder(R.drawable.bg_card_rounded)
                        .error(R.drawable.bg_card_rounded)
                        .into(binding.ivPlaylistCover);
            }
        }
    }

    private void setupToolbar() {
        binding.toolbarDetail.setNavigationOnClickListener(v -> finish());

        binding.appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            int totalScrollRange = appBarLayout.getTotalScrollRange();
            if (totalScrollRange == 0) return;
            float ratio = (float) Math.abs(verticalOffset) / (float) totalScrollRange;
            binding.tvToolbarTitle.setAlpha(ratio >= 0.8f ? (ratio - 0.8f) / 0.2f : 0f);
        });
    }

    private void setupRecyclerView() {
        trackAdapter = new SearchResultAdapter(this);
        binding.rvPlaylistTracks.setLayoutManager(new LinearLayoutManager(this));
        binding.rvPlaylistTracks.setAdapter(trackAdapter);
    }

    private void setupActions() {
        binding.btnPlaylistPlayAll.setOnClickListener(v -> {
            if (!songList.isEmpty()) {
                MusicPlayerManager.getInstance().playQueue(songList, 0);
                Song first = songList.get(0);
                openPlayerActivity(first);
            } else {
                Toast.makeText(this, "Danh sách bài hát đang trống", Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnPlaylistFavorite.setOnClickListener(v -> {
            isPlaylistLiked = !isPlaylistLiked;
            updatePlaylistFavoriteUi(isPlaylistLiked);
            binding.btnPlaylistFavorite.animate()
                    .scaleX(1.3f).scaleY(1.3f)
                    .setDuration(100)
                    .withEndAction(() -> binding.btnPlaylistFavorite.animate().scaleX(1f).scaleY(1f).setDuration(100).start())
                    .start();
            Toast.makeText(this, isPlaylistLiked ? "Đã lưu vào Thư viện của bạn ❤️" : "Đã xóa khỏi Thư viện", Toast.LENGTH_SHORT).show();
        });

        binding.btnPlaylistMore.setOnClickListener(v -> showPlaylistOptionsBottomSheet());

        binding.btnExploreTracks.setOnClickListener(v -> finish());
    }

    private void updatePlaylistFavoriteUi(boolean liked) {
        binding.btnPlaylistFavorite.setImageResource(
                liked ? R.drawable.ic_favorite_filled : R.drawable.ic_favorite_border
        );
        int color = liked ? getColor(R.color.spotify_green) : getColor(R.color.text_secondary);
        binding.btnPlaylistFavorite.setColorFilter(color);
    }

    private void showPlaylistOptionsBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_Spotify_BottomSheet);
        LayoutBottomSheetPlaylistOptionsBinding optionsBinding =
                LayoutBottomSheetPlaylistOptionsBinding.inflate(getLayoutInflater());
        dialog.setContentView(optionsBinding.getRoot());

        String title = binding.tvPlaylistTitle.getText().toString();
        optionsBinding.tvPlaylistName.setText(title);
        optionsBinding.tvPlaylistSubtitle.setText(songList.size() + " bài hát • Danh sách phát");

        String cover = getIntent().getStringExtra(EXTRA_PLAYLIST_COVER);
        if (cover != null && !cover.isEmpty()) {
            Glide.with(this)
                    .load(ApiClient.resolveMediaUrl(cover))
                    .placeholder(R.drawable.bg_card_rounded)
                    .error(R.drawable.bg_card_rounded)
                    .into(optionsBinding.ivPlaylistArt);
        }

        // 1. Chia sẻ
        optionsBinding.itemPlaylistShare.setOnClickListener(v -> {
            dialog.dismiss();
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Chia sẻ danh sách phát");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Nghe danh sách phát \"" + title + "\" trên Vibe Music! 🎶");
            startActivity(Intent.createChooser(shareIntent, "Chia sẻ danh sách phát qua"));
        });

        // 2. Tải xuống ngoại tuyến
        optionsBinding.itemPlaylistDownload.setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "Đang tải xuống " + songList.size() + " bài hát để nghe ngoại tuyến...", Toast.LENGTH_SHORT).show();
        });

        // 3. Hiển thị trên hồ sơ
        optionsBinding.itemPlaylistAddToProfile.setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "Đã bật hiển thị danh sách phát trên hồ sơ của bạn", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void showLoading(boolean isLoading) {
        if (binding == null) return;
        if (isLoading) {
            binding.shimmerPlaylistContainer.setVisibility(View.VISIBLE);
            binding.shimmerPlaylistContainer.startShimmer();
            binding.rvPlaylistTracks.setVisibility(View.GONE);
            binding.layoutPlaylistEmptyState.setVisibility(View.GONE);
        } else {
            binding.shimmerPlaylistContainer.stopShimmer();
            binding.shimmerPlaylistContainer.setVisibility(View.GONE);
            if (songList.isEmpty()) {
                binding.rvPlaylistTracks.setVisibility(View.GONE);
                binding.layoutPlaylistEmptyState.setVisibility(View.VISIBLE);
            } else {
                binding.rvPlaylistTracks.setVisibility(View.VISIBLE);
                binding.layoutPlaylistEmptyState.setVisibility(View.GONE);
            }
        }
    }

    private void loadPlaylistData() {
        showLoading(true);
        if (TYPE_LIKED_SONGS.equals(playlistType)) {
            loadLikedSongs();
        } else if (TYPE_ALBUM.equals(playlistType) && playlistId != null && !playlistId.isEmpty()) {
            loadAlbumDetails(playlistId);
        } else if (playlistId != null && !playlistId.isEmpty()) {
            loadCustomPlaylist(playlistId);
        } else {
            showLoading(false);
        }
    }

    private void loadAlbumDetails(String id) {
        ApiClient.getApiService().getAlbumDetails(id).enqueue(new Callback<ApiResponse<Album>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Album>> call, @NonNull Response<ApiResponse<Album>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    Album album = response.body().getData();
                    binding.tvPlaylistTitle.setText(album.getTitle());
                    binding.tvToolbarTitle.setText(album.getTitle());

                    List<Song> tracks = album.getTracks();
                    songList.clear();
                    if (tracks != null) {
                        songList.addAll(tracks);
                    }
                    trackAdapter.setSongs(songList);
                    binding.tvPlaylistDetails.setText(album.getArtistName() + " • " + album.getReleaseYear() + " • " + songList.size() + " bài hát");

                    if (album.getCoverImageUrl() != null && !album.getCoverImageUrl().isEmpty()) {
                        Glide.with(PlaylistDetailActivity.this)
                                .load(ApiClient.resolveMediaUrl(album.getCoverImageUrl()))
                                .placeholder(R.drawable.bg_card_rounded)
                                .error(R.drawable.bg_card_rounded)
                                .into(binding.ivPlaylistCover);
                    }
                } else {
                    binding.tvPlaylistDetails.setText("Chưa có bài hát trong album");
                }
                showLoading(false);
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<Album>> call, @NonNull Throwable t) {
                Toast.makeText(PlaylistDetailActivity.this, "Lỗi tải album: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                binding.tvPlaylistDetails.setText("Không thể kết nối máy chủ");
                showLoading(false);
            }
        });
    }

    private void loadLikedSongs() {
        ApiClient.getApiService().getFavorites().enqueue(new Callback<ApiResponse<FavoritesData>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<FavoritesData>> call, @NonNull Response<ApiResponse<FavoritesData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    List<Song> songs = response.body().getData().getSongs();
                    songList.clear();
                    if (songs != null) {
                        for (Song s : songs) {
                            s.setLiked(true);
                        }
                        songList.addAll(songs);
                    }
                    trackAdapter.setSongs(songList);
                    binding.tvPlaylistDetails.setText(songList.size() + " bài hát • Yêu thích của bạn");
                } else {
                    binding.tvPlaylistDetails.setText("Chưa có bài hát yêu thích");
                }
                showLoading(false);
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<FavoritesData>> call, @NonNull Throwable t) {
                Toast.makeText(PlaylistDetailActivity.this, "Lỗi tải bài hát: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                binding.tvPlaylistDetails.setText("Không thể kết nối máy chủ");
                showLoading(false);
            }
        });
    }

    private void loadCustomPlaylist(String id) {
        ApiClient.getApiService().getPlaylistDetails(id).enqueue(new Callback<ApiResponse<Playlist>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Playlist>> call, @NonNull Response<ApiResponse<Playlist>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    Playlist playlist = response.body().getData();
                    binding.tvPlaylistTitle.setText(playlist.getTitle());
                    binding.tvToolbarTitle.setText(playlist.getTitle());

                    List<Song> tracks = playlist.getTracks();
                    songList.clear();
                    if (tracks != null) {
                        songList.addAll(tracks);
                    }
                    trackAdapter.setSongs(songList);
                    binding.tvPlaylistDetails.setText(songList.size() + " bài hát • " +
                            (playlist.getDescription() != null ? playlist.getDescription() : ""));

                    if (playlist.getCoverImageUrl() != null && !playlist.getCoverImageUrl().isEmpty()) {
                        Glide.with(PlaylistDetailActivity.this)
                                .load(ApiClient.resolveMediaUrl(playlist.getCoverImageUrl()))
                                .placeholder(R.drawable.bg_card_rounded)
                                .error(R.drawable.bg_card_rounded)
                                .into(binding.ivPlaylistCover);
                    }
                } else {
                    Toast.makeText(PlaylistDetailActivity.this, "Không thể tải chi tiết danh sách phát", Toast.LENGTH_SHORT).show();
                }
                showLoading(false);
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<Playlist>> call, @NonNull Throwable t) {
                Toast.makeText(PlaylistDetailActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                showLoading(false);
            }
        });
    }

    @Override
    public void onSongClick(Song song) {
        if (song == null) return;
        int index = songList.indexOf(song);
        if (index == -1) index = 0;
        MusicPlayerManager.getInstance().playQueue(songList, index);
        openPlayerActivity(song);
    }

    private void openPlayerActivity(Song song) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("SONG_TITLE", song.getTitle());
        intent.putExtra("ARTIST_NAME", song.getArtistName());
        intent.putExtra("COVER_URL", ApiClient.resolveMediaUrl(song.getCoverImageUrl()));
        intent.putExtra("AUDIO_URL", ApiClient.resolveMediaUrl(song.getAudioUrl()));
        intent.putExtra("SONG_ID", song.getId());
        intent.putExtra("IS_LIKED", song.isLiked());
        intent.putExtra("DURATION", song.getDuration());
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_bottom, R.anim.stay);
    }
}
