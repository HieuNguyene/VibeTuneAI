package com.example.spotifyclone.presentation.playlist;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.spotifyclone.R;
import com.example.spotifyclone.data.model.Album;
import com.example.spotifyclone.data.model.ApiResponse;
import com.example.spotifyclone.data.model.FavoritesData;
import com.example.spotifyclone.data.model.Playlist;
import com.example.spotifyclone.data.model.Song;
import com.example.spotifyclone.data.model.ToggleFavoriteData;
import com.example.spotifyclone.data.remote.ApiClient;
import com.example.spotifyclone.databinding.ActivityPlaylistDetailBinding;
import com.example.spotifyclone.databinding.LayoutBottomSheetPlaylistOptionsBinding;
import com.example.spotifyclone.player.MusicPlayerManager;
import com.example.spotifyclone.presentation.main.MainActivity;
import com.example.spotifyclone.presentation.player.PlayerActivity;
import com.example.spotifyclone.presentation.search.SearchResultAdapter;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    public static final String EXTRA_IS_FAVORITE = "IS_FAVORITE";
    public static final String EXTRA_ORIGIN_DESTINATION_ID = "ORIGIN_DESTINATION_ID";

    private ActivityPlaylistDetailBinding binding;
    private SearchResultAdapter trackAdapter;
    private final List<Song> songList = new ArrayList<>();
    private String playlistType = TYPE_LIKED_SONGS;
    private String playlistId = "";
    private boolean isPlaylistLiked = false;
    private Playlist currentPlaylist = null;
    private int originDestId = R.id.navigation_home;
    private boolean isCurrentSongFavorite = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ApiClient.init(this);
        binding = ActivityPlaylistDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        com.example.spotifyclone.util.ThemeManager.applySystemBarsAppearance(this);

        parseIntent();
        setupWindowInsets();
        setupToolbar();
        setupRecyclerView();
        setupNavigationDock();
        setupMiniPlayer();
        setupActions();
        loadPlaylistData();
    }

    private void parseIntent() {
        playlistType = getIntent().getStringExtra(EXTRA_PLAYLIST_TYPE);
        if (playlistType == null) playlistType = TYPE_LIKED_SONGS;
        playlistId = getIntent().getStringExtra(EXTRA_PLAYLIST_ID);
        originDestId = getIntent().getIntExtra(EXTRA_ORIGIN_DESTINATION_ID, R.id.navigation_home);

        String title = getIntent().getStringExtra(EXTRA_PLAYLIST_TITLE);
        String cover = getIntent().getStringExtra(EXTRA_PLAYLIST_COVER);
        isPlaylistLiked = getIntent().getBooleanExtra(EXTRA_IS_FAVORITE, false);

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
        binding.btnPlaylistFavorite.setVisibility(TYPE_LIKED_SONGS.equals(playlistType) ? View.GONE : View.VISIBLE);
        updatePlaylistFavoriteUi(isPlaylistLiked);
    }

    private void setupWindowInsets() {
        final int baseAppBarHeight = binding.appBarLayout.getLayoutParams() != null ? binding.appBarLayout.getLayoutParams().height : 0;
        final int baseHeaderTopMargin = (binding.ivPlaylistCover.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)
                ? ((ViewGroup.MarginLayoutParams) binding.ivPlaylistCover.getLayoutParams()).topMargin : 0;

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, windowInsets) -> {
            androidx.core.graphics.Insets bars = windowInsets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars() | androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            );

            // 1. Toolbar top padding & height
            int actionBarHeight = 0;
            android.util.TypedValue tv = new android.util.TypedValue();
            if (getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
                actionBarHeight = android.util.TypedValue.complexToDimensionPixelSize(tv.data, getResources().getDisplayMetrics());
            }
            if (actionBarHeight <= 0) {
                actionBarHeight = Math.round(56 * getResources().getDisplayMetrics().density);
            }

            ViewGroup.LayoutParams toolbarLp = binding.toolbarDetail.getLayoutParams();
            if (toolbarLp != null) {
                toolbarLp.height = actionBarHeight + bars.top;
                binding.toolbarDetail.setLayoutParams(toolbarLp);
            }
            binding.toolbarDetail.setPadding(
                    binding.toolbarDetail.getPaddingLeft(),
                    bars.top,
                    binding.toolbarDetail.getPaddingRight(),
                    binding.toolbarDetail.getPaddingBottom()
            );

            // 2. AppBarLayout height
            if (baseAppBarHeight > 0) {
                ViewGroup.LayoutParams appBarLp = binding.appBarLayout.getLayoutParams();
                if (appBarLp != null) {
                    appBarLp.height = baseAppBarHeight + bars.top;
                    binding.appBarLayout.setLayoutParams(appBarLp);
                }
            }

            // 3. ivPlaylistCover top margin
            if (binding.ivPlaylistCover.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams marginLp = (ViewGroup.MarginLayoutParams) binding.ivPlaylistCover.getLayoutParams();
                marginLp.topMargin = baseHeaderTopMargin + bars.top;
                binding.ivPlaylistCover.setLayoutParams(marginLp);
            }

            // 4. bottomNavigationContainer padding for system gesture navigation
            float d = getResources().getDisplayMetrics().density;
            int padH = Math.round(12 * d);
            int padTop = Math.round(4 * d);
            binding.bottomNavigationContainer.setPadding(padH, padTop, padH, bars.bottom);

            binding.bottomNavigationContainer.post(this::syncScrollContentBottomPadding);

            return windowInsets;
        });

        if (binding.getRoot().isAttachedToWindow()) {
            androidx.core.view.ViewCompat.requestApplyInsets(binding.getRoot());
        }

        binding.bottomNavigationContainer.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            syncScrollContentBottomPadding();
        });
    }

    private void syncScrollContentBottomPadding() {
        if (binding == null) return;
        int containerHeight = binding.bottomNavigationContainer.getHeight();
        if (containerHeight <= 0) {
            float d = getResources().getDisplayMetrics().density;
            containerHeight = Math.round(80 * d);
        }
        binding.scrollPlaylistContent.setClipToPadding(false);
        if (binding.scrollPlaylistContent.getPaddingBottom() != containerHeight) {
            binding.scrollPlaylistContent.setPadding(
                    binding.scrollPlaylistContent.getPaddingLeft(),
                    binding.scrollPlaylistContent.getPaddingTop(),
                    binding.scrollPlaylistContent.getPaddingRight(),
                    containerHeight
            );
        }
    }

    private void setupNavigationDock() {
        binding.liquidNavBar.setBackdropSource(binding.scrollPlaylistContent);
        binding.miniPlayerGlassFrame.setBackdropSource(binding.scrollPlaylistContent);

        binding.liquidNavBar.setSelectedItemId(originDestId, false);

        binding.liquidNavBar.setOnTabClickListener((item, index) -> {
            int itemId = item.getItemId();
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra("NAV_TARGET_ID", itemId);
            startActivity(intent);
            finish();
            return true;
        });

        binding.scrollPlaylistContent.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener)
                (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    binding.liquidNavBar.markBackdropDirty();
                    binding.miniPlayerGlassFrame.markBackdropDirty();
                });
    }

    private void setMiniPlayerVisible(boolean isVisible) {
        if (binding == null) return;
        int vis = isVisible ? View.VISIBLE : View.GONE;
        binding.miniPlayerGlassFrame.setVisibility(vis);
        binding.includedMiniPlayer.miniPlayerRoot.setVisibility(vis);
        binding.bottomNavigationContainer.post(this::syncScrollContentBottomPadding);
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private void setupMiniPlayer() {
        com.example.spotifyclone.ui.liquid.LiquidDropletHelper.attachDropletSpring(
                binding.includedMiniPlayer.btnMiniFavorite,
                binding.includedMiniPlayer.btnMiniPlayPause
        );

        updateMiniPlayerUiFromPlayer();

        // GestureDetector: Chạm mở Fullscreen, Vuốt trái qua bài, Vuốt phải lùi bài
        android.view.GestureDetector miniPlayerGestureDetector = new android.view.GestureDetector(this, new android.view.GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(android.view.MotionEvent e) {
                openFullscreenPlayer();
                return true;
            }

            @Override
            public boolean onFling(android.view.MotionEvent e1, android.view.MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > 80 && Math.abs(velocityX) > 100) {
                    if (diffX < 0) {
                        MusicPlayerManager.getInstance().playNext();
                        Toast.makeText(PlaylistDetailActivity.this, "Đang chuyển bài tiếp theo", Toast.LENGTH_SHORT).show();
                    } else {
                        MusicPlayerManager.getInstance().playPrevious();
                        Toast.makeText(PlaylistDetailActivity.this, "Đang quay lại bài trước", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
                return false;
            }
        });

        binding.includedMiniPlayer.miniPlayerRoot.setOnTouchListener((v, event) -> {
            miniPlayerGestureDetector.onTouchEvent(event);
            return true;
        });

        binding.includedMiniPlayer.btnMiniPlayPause.setOnClickListener(v -> {
            MusicPlayerManager.getInstance().togglePlayPause();
        });

        binding.includedMiniPlayer.btnMiniFavorite.setOnClickListener(v -> {
            toggleCurrentSongFavorite();
        });
    }

    private void updateMiniPlayerUiFromPlayer() {
        if (binding == null) return;
        String title = MusicPlayerManager.getInstance().getCurrentTitle();
        if (title.isEmpty()) {
            setMiniPlayerVisible(false);
        } else {
            setMiniPlayerVisible(true);
            binding.includedMiniPlayer.tvMiniSongTitle.setText(title);
            binding.includedMiniPlayer.tvMiniArtistName.setText(MusicPlayerManager.getInstance().getCurrentArtist());
            Glide.with(this)
                    .load(MusicPlayerManager.getInstance().getCurrentCoverUrl())
                    .placeholder(R.drawable.bg_card_rounded)
                    .error(R.drawable.bg_card_rounded)
                    .into(binding.includedMiniPlayer.ivMiniAlbumArt);
            binding.includedMiniPlayer.btnMiniPlayPause.setImageResource(
                    MusicPlayerManager.getInstance().isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play_arrow
            );
            isCurrentSongFavorite = MusicPlayerManager.getInstance().isCurrentLiked();
            updateMiniPlayerFavoriteIcon(isCurrentSongFavorite);
            int cur = MusicPlayerManager.getInstance().getCurrentPosition();
            int total = MusicPlayerManager.getInstance().getDuration();
            if (total > 0) {
                binding.includedMiniPlayer.miniPlayerProgressBar.setMax(total);
                binding.includedMiniPlayer.miniPlayerProgressBar.setProgress(cur);
            }
        }
    }

    private void updateMiniPlayerFavoriteIcon(boolean liked) {
        if (binding == null) return;
        binding.includedMiniPlayer.btnMiniFavorite.setImageResource(
                liked ? R.drawable.ic_favorite_filled : R.drawable.ic_favorite_border
        );
        int tintColor = liked ? getColor(R.color.spotify_green) : getColor(R.color.text_secondary);
        binding.includedMiniPlayer.btnMiniFavorite.setColorFilter(tintColor);
    }

    private void toggleCurrentSongFavorite() {
        String songId = MusicPlayerManager.getInstance().getCurrentSongId();
        if (songId == null || songId.isEmpty()) {
            isCurrentSongFavorite = !isCurrentSongFavorite;
            updateMiniPlayerFavoriteIcon(isCurrentSongFavorite);
            MusicPlayerManager.getInstance().setCurrentSongLiked(isCurrentSongFavorite);
            return;
        }

        boolean nextLiked = !isCurrentSongFavorite;
        updateMiniPlayerFavoriteIcon(nextLiked);

        ApiClient.getApiService().toggleFavorite(songId).enqueue(new Callback<ApiResponse<ToggleFavoriteData>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<ToggleFavoriteData>> call,
                                   @NonNull Response<ApiResponse<ToggleFavoriteData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    boolean serverLiked = response.body().getData().isLiked();
                    isCurrentSongFavorite = serverLiked;
                    updateMiniPlayerFavoriteIcon(isCurrentSongFavorite);
                    MusicPlayerManager.getInstance().setCurrentSongLiked(isCurrentSongFavorite);
                    syncSongLikedState(songId, isCurrentSongFavorite);
                    String msg = isCurrentSongFavorite ? "Đã thêm vào bài hát yêu thích" : "Đã xóa khỏi bài hát yêu thích";
                    Toast.makeText(PlaylistDetailActivity.this, msg, Toast.LENGTH_SHORT).show();
                } else {
                    updateMiniPlayerFavoriteIcon(isCurrentSongFavorite);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<ToggleFavoriteData>> call, @NonNull Throwable t) {
                updateMiniPlayerFavoriteIcon(isCurrentSongFavorite);
            }
        });
    }

    private void openFullscreenPlayer() {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("SONG_TITLE", MusicPlayerManager.getInstance().getCurrentTitle());
        intent.putExtra("ARTIST_NAME", MusicPlayerManager.getInstance().getCurrentArtist());
        intent.putExtra("COVER_URL", MusicPlayerManager.getInstance().getCurrentCoverUrl());
        intent.putExtra("AUDIO_URL", MusicPlayerManager.getInstance().getCurrentAudioUrl());
        intent.putExtra("SONG_ID", MusicPlayerManager.getInstance().getCurrentSongId());
        intent.putExtra("IS_LIKED", MusicPlayerManager.getInstance().isCurrentLiked());
        intent.putExtra("DURATION", MusicPlayerManager.getInstance().getDuration());
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_bottom, R.anim.stay);
    }

    private void setupToolbar() {
        binding.toolbarDetail.setNavigationOnClickListener(v -> finish());

        binding.appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            int totalScrollRange = appBarLayout.getTotalScrollRange();
            if (totalScrollRange == 0) return;
            float ratio = (float) Math.abs(verticalOffset) / (float) totalScrollRange;
            binding.layoutPlaylistHeaderParallax.setAlpha(Math.max(0f, 1f - ratio * 1.6f));
            binding.tvToolbarTitle.setAlpha(ratio >= 0.8f ? (ratio - 0.8f) / 0.2f : 0f);
        });
    }

    private void setupRecyclerView() {
        trackAdapter = new SearchResultAdapter(this);
        binding.rvPlaylistTracks.setLayoutManager(new LinearLayoutManager(this));
        binding.rvPlaylistTracks.setAdapter(trackAdapter);

        trackAdapter.setOnSongActionListener((song, position) -> {
            String targetPlaylistId = isCurrentPlaylistOwner() ? playlistId : null;
            com.example.spotifyclone.presentation.player.SongOptionsBottomSheetDialog.show(
                    PlaylistDetailActivity.this,
                    song,
                    targetPlaylistId,
                    () -> {
                        if (position >= 0 && position < songList.size()) {
                            songList.remove(position);
                            trackAdapter.setSongs(songList);
                            if (songList.isEmpty()) {
                                binding.rvPlaylistTracks.setVisibility(View.GONE);
                                binding.layoutPlaylistEmptyState.setVisibility(View.VISIBLE);
                            }
                            String desc = (currentPlaylist != null && currentPlaylist.getDescription() != null) ?
                                    currentPlaylist.getDescription() : "";
                            binding.tvPlaylistDetails.setText(songList.size() + " bài hát • " + desc);
                        }
                    },
                    isLiked -> {
                        song.setLiked(isLiked);
                        trackAdapter.notifyItemChanged(position);
                    }
            );
        });
    }

    private void setupActions() {
        com.example.spotifyclone.ui.liquid.LiquidDropletHelper.attachDropletSpring(binding.btnPlaylistPlayAll);
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
            if (TYPE_ALBUM.equals(playlistType)) {
                ApiClient.getApiService().toggleFavoriteAlbum(playlistId).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse<Map<String, Object>>> call, @NonNull Response<ApiResponse<Map<String, Object>>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Map<String, Object> data = response.body().getData();
                            if (data != null && data.get("isFavorite") instanceof Boolean) {
                                isPlaylistLiked = (Boolean) data.get("isFavorite");
                            } else {
                                isPlaylistLiked = !isPlaylistLiked;
                            }
                            updatePlaylistFavoriteUi(isPlaylistLiked);
                            animateFavoriteButton();
                            Toast.makeText(PlaylistDetailActivity.this,
                                    isPlaylistLiked ? "Đã lưu Album vào mục Cá nhân hóa" : "Đã xóa Album khỏi mục Cá nhân hóa",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(PlaylistDetailActivity.this, "Không thể cập nhật yêu thích album", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse<Map<String, Object>>> call, @NonNull Throwable t) {
                        Toast.makeText(PlaylistDetailActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                    }
                });
            } else if (!TYPE_LIKED_SONGS.equals(playlistType) && playlistId != null && !playlistId.isEmpty()) {
                ApiClient.getApiService().toggleFavoritePlaylist(playlistId).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse<Map<String, Object>>> call, @NonNull Response<ApiResponse<Map<String, Object>>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Map<String, Object> data = response.body().getData();
                            if (data != null && data.get("isFavorite") instanceof Boolean) {
                                isPlaylistLiked = (Boolean) data.get("isFavorite");
                            } else {
                                isPlaylistLiked = !isPlaylistLiked;
                            }
                            if (currentPlaylist != null) {
                                currentPlaylist.setFavorite(isPlaylistLiked);
                            }
                            updatePlaylistFavoriteUi(isPlaylistLiked);
                            animateFavoriteButton();
                            Toast.makeText(PlaylistDetailActivity.this,
                                    isPlaylistLiked ? "Đã lưu Playlist vào mục Cá nhân hóa" : "Đã xóa Playlist khỏi mục Cá nhân hóa",
                                    Toast.LENGTH_SHORT).show();
                        } else if (response.code() == 403) {
                            Toast.makeText(PlaylistDetailActivity.this, "Danh sách phát này là riêng tư và đã bị khóa bởi người tạo", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(PlaylistDetailActivity.this, "Không thể cập nhật yêu thích playlist", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse<Map<String, Object>>> call, @NonNull Throwable t) {
                        Toast.makeText(PlaylistDetailActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        binding.btnPlaylistMore.setOnClickListener(v -> showPlaylistOptionsBottomSheet());

        binding.btnExploreTracks.setOnClickListener(v -> finish());
    }

    private void animateFavoriteButton() {
        binding.btnPlaylistFavorite.animate()
                .scaleX(1.3f).scaleY(1.3f)
                .setDuration(100)
                .withEndAction(() -> binding.btnPlaylistFavorite.animate().scaleX(1f).scaleY(1f).setDuration(100).start())
                .start();
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
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Nghe danh sách phát \"" + title + "\".");
            startActivity(Intent.createChooser(shareIntent, "Chia sẻ danh sách phát qua"));
        });

        // 2. Chỉnh sửa & 3. Xóa danh sách phát (Chỉ chủ sở hữu Playlist)
        boolean isOwner = isCurrentPlaylistOwner();
        if (isOwner) {
            optionsBinding.itemPlaylistEdit.setVisibility(View.VISIBLE);
            optionsBinding.itemPlaylistDelete.setVisibility(View.VISIBLE);

            optionsBinding.itemPlaylistEdit.setOnClickListener(v -> {
                dialog.dismiss();
                showEditPlaylistDialog();
            });

            optionsBinding.itemPlaylistDelete.setOnClickListener(v -> {
                dialog.dismiss();
                confirmDeletePlaylist();
            });
        } else {
            optionsBinding.itemPlaylistEdit.setVisibility(View.GONE);
            optionsBinding.itemPlaylistDelete.setVisibility(View.GONE);
        }

        dialog.show();
    }

    private boolean isCurrentPlaylistOwner() {
        if (!TYPE_CUSTOM_PLAYLIST.equals(playlistType) || currentPlaylist == null) return false;
        com.example.spotifyclone.data.model.User currentUser =
                com.example.spotifyclone.data.local.SessionManager.getInstance(this).getUser();
        if (currentUser == null || currentUser.getId() == null) return false;
        String creatorId = currentPlaylist.getCreatorIdString();
        return currentUser.getId().equals(creatorId);
    }

    private void showEditPlaylistDialog() {
        if (currentPlaylist == null) return;
        CreatePlaylistBottomSheetDialog editDialog = CreatePlaylistBottomSheetDialog.newEditInstance(currentPlaylist);
        editDialog.setOnPlaylistUpdatedListener(updatedPlaylist -> {
            currentPlaylist = updatedPlaylist;
            binding.tvPlaylistTitle.setText(updatedPlaylist.getTitle());
            binding.tvToolbarTitle.setText(updatedPlaylist.getTitle());
            binding.tvPlaylistDetails.setText(songList.size() + " bài hát • " +
                    (updatedPlaylist.getDescription() != null ? updatedPlaylist.getDescription() : ""));
            if (updatedPlaylist.getCoverImageUrl() != null && !updatedPlaylist.getCoverImageUrl().isEmpty()) {
                Glide.with(this)
                        .load(ApiClient.resolveMediaUrl(updatedPlaylist.getCoverImageUrl()))
                        .placeholder(R.drawable.bg_card_rounded)
                        .error(R.drawable.bg_card_rounded)
                        .into(binding.ivPlaylistCover);
            }
        });
        editDialog.show(getSupportFragmentManager(), "EditPlaylistBottomSheet");
    }

    private void confirmDeletePlaylist() {
        if (currentPlaylist == null) return;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Xóa danh sách phát")
                .setMessage("Bạn có chắc chắn muốn xóa danh sách phát \"" + currentPlaylist.getTitle() + "\" không? Thao tác này không thể hoàn tác.")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    ApiClient.getApiService().deletePlaylist(playlistId).enqueue(new Callback<ApiResponse<Object>>() {
                        @Override
                        public void onResponse(@NonNull Call<ApiResponse<Object>> call, @NonNull Response<ApiResponse<Object>> response) {
                            if (response.isSuccessful()) {
                                Toast.makeText(PlaylistDetailActivity.this, "Đã xóa danh sách phát thành công", Toast.LENGTH_SHORT).show();
                                finish();
                            } else {
                                Toast.makeText(PlaylistDetailActivity.this, "Không thể xóa danh sách phát", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<ApiResponse<Object>> call, @NonNull Throwable t) {
                            Toast.makeText(PlaylistDetailActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Hủy", null)
                .show();
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
                    isPlaylistLiked = album.isFavorite();
                    updatePlaylistFavoriteUi(isPlaylistLiked);
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
                    currentPlaylist = playlist;
                    binding.tvPlaylistTitle.setText(playlist.getTitle());
                    binding.tvToolbarTitle.setText(playlist.getTitle());

                    List<Song> tracks = playlist.getTracks();
                    songList.clear();
                    if (tracks != null) {
                        songList.addAll(tracks);
                    }
                    trackAdapter.setSongs(songList);
                    isPlaylistLiked = playlist.isFavorite();
                    updatePlaylistFavoriteUi(isPlaylistLiked);
                    binding.tvPlaylistDetails.setText(songList.size() + " bài hát • " +
                            (playlist.getDescription() != null ? playlist.getDescription() : ""));

                    if (playlist.getCoverImageUrl() != null && !playlist.getCoverImageUrl().isEmpty()) {
                        Glide.with(PlaylistDetailActivity.this)
                                .load(ApiClient.resolveMediaUrl(playlist.getCoverImageUrl()))
                                .placeholder(R.drawable.bg_card_rounded)
                                .error(R.drawable.bg_card_rounded)
                                .into(binding.ivPlaylistCover);
                    }
                } else if (response.code() == 403) {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(PlaylistDetailActivity.this)
                            .setTitle("Danh sách phát riêng tư")
                            .setMessage("Danh sách phát này là riêng tư và đã bị khóa bởi người tạo.")
                            .setPositiveButton("Đóng", (d, w) -> finish())
                            .setCancelable(false)
                            .show();
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

    private final MusicPlayerManager.PlayerListener playerListener = new MusicPlayerManager.PlayerListener() {
        @Override
        public void onTrackChanged(String title, String artist, String coverUrl) {
            runOnUiThread(() -> {
                setMiniPlayerVisible(true);
                binding.includedMiniPlayer.tvMiniSongTitle.setText(title);
                binding.includedMiniPlayer.tvMiniArtistName.setText(artist);
                Glide.with(PlaylistDetailActivity.this)
                        .load(coverUrl)
                        .placeholder(R.drawable.bg_card_rounded)
                        .error(R.drawable.bg_card_rounded)
                        .into(binding.includedMiniPlayer.ivMiniAlbumArt);
            });
        }

        @Override
        public void onPlaybackStateChanged(boolean isPlaying) {
            runOnUiThread(() -> {
                binding.includedMiniPlayer.btnMiniPlayPause.setImageResource(
                        isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow
                );
            });
        }

        @Override
        public void onProgressUpdate(int currentMs, int totalMs) {
            runOnUiThread(() -> {
                if (totalMs > 0) {
                    binding.includedMiniPlayer.miniPlayerProgressBar.setMax(totalMs);
                    binding.includedMiniPlayer.miniPlayerProgressBar.setProgress(currentMs);
                }
            });
        }

        @Override
        public void onError(String message) {
            runOnUiThread(() -> Toast.makeText(PlaylistDetailActivity.this, message, Toast.LENGTH_SHORT).show());
        }

        @Override
        public void onFavoriteStateChanged(String songId, boolean isLiked) {
            runOnUiThread(() -> {
                String currentId = MusicPlayerManager.getInstance().getCurrentSongId();
                if (currentId != null && currentId.equals(songId)) {
                    isCurrentSongFavorite = isLiked;
                    updateMiniPlayerFavoriteIcon(isLiked);
                }
                syncSongLikedState(songId, isLiked);
            });
        }
    };

    private void syncSongLikedState(String songId, boolean liked) {
        if (songId == null || songList.isEmpty()) return;
        for (int i = 0; i < songList.size(); i++) {
            Song s = songList.get(i);
            if (s != null && songId.equals(s.getId())) {
                if (s.isLiked() != liked) {
                    s.setLiked(liked);
                    if (trackAdapter != null) {
                        trackAdapter.notifyItemChanged(i);
                    }
                }
                break;
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        MusicPlayerManager.getInstance().addListener(playerListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        com.example.spotifyclone.util.ThemeManager.applySystemBarsAppearance(this);
        updateMiniPlayerUiFromPlayer();
        if (binding != null) {
            binding.liquidNavBar.markBackdropDirty();
            binding.miniPlayerGlassFrame.invalidate();
            syncScrollContentBottomPadding();
        }
        String currentId = MusicPlayerManager.getInstance().getCurrentSongId();
        if (currentId != null && !currentId.isEmpty()) {
            syncSongLikedState(currentId, MusicPlayerManager.getInstance().isCurrentLiked());
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        MusicPlayerManager.getInstance().removeListener(playerListener);
    }
}
