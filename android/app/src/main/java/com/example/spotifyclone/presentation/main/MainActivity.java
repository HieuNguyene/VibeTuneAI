package com.example.spotifyclone.presentation.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.bumptech.glide.Glide;
import com.example.spotifyclone.R;
import com.example.spotifyclone.data.model.ApiResponse;
import com.example.spotifyclone.data.model.Song;
import com.example.spotifyclone.data.model.ToggleFavoriteData;
import com.example.spotifyclone.data.remote.ApiClient;
import com.example.spotifyclone.databinding.ActivityMainBinding;
import com.example.spotifyclone.player.MusicPlayerManager;
import com.example.spotifyclone.presentation.player.PlayerActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity implements MusicPlayerManager.PlayerListener {

    private ActivityMainBinding binding;
    private boolean isFavorite = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ApiClient.init(this);

        // Kiem tra phien dang nhap (Auth Guard)
        if (!com.example.spotifyclone.data.local.SessionManager.getInstance(this).isLoggedIn()) {
            Intent intent = new Intent(this, com.example.spotifyclone.presentation.auth.LoginActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Bù trừ an toàn thanh trạng thái (Status Bar) và thanh điều hướng (Navigation Bar)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.mainRootLayout, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars() | androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            );

            android.view.ViewGroup.LayoutParams lp = binding.statusBarSpacer.getLayoutParams();
            if (lp.height != bars.top) {
                lp.height = bars.top;
                binding.statusBarSpacer.setLayoutParams(lp);
            }

            binding.bottomNavigationContainer.setPadding(0, 0, 0, bars.bottom);
            return insets;
        });
        if (binding.mainRootLayout.isAttachedToWindow()) {
            androidx.core.view.ViewCompat.requestApplyInsets(binding.mainRootLayout);
        }

        MusicPlayerManager.getInstance().addListener(this);

        setupNavigation();
        setupMiniPlayer();
        checkAndRequestNotificationPermission();
    }

    private final androidx.activity.result.ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), isGranted -> {
                android.util.Log.d("MainActivity", "Notification permission granted: " + isGranted);
            });

    private void checkAndRequestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void setupNavigation() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(binding.bottomNavigationView, navController);
        }
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private void setupMiniPlayer() {
        if (MusicPlayerManager.getInstance().getCurrentTitle().isEmpty()) {
            binding.includedMiniPlayer.miniPlayerRoot.setVisibility(View.GONE);
        } else {
            binding.includedMiniPlayer.miniPlayerRoot.setVisibility(View.VISIBLE);
            binding.includedMiniPlayer.tvMiniSongTitle.setText(MusicPlayerManager.getInstance().getCurrentTitle());
            binding.includedMiniPlayer.tvMiniArtistName.setText(MusicPlayerManager.getInstance().getCurrentArtist());
            Glide.with(this)
                    .load(MusicPlayerManager.getInstance().getCurrentCoverUrl())
                    .placeholder(R.drawable.bg_card_rounded)
                    .error(R.drawable.bg_card_rounded)
                    .into(binding.includedMiniPlayer.ivMiniAlbumArt);
            binding.includedMiniPlayer.btnMiniPlayPause.setImageResource(
                    MusicPlayerManager.getInstance().isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play_arrow
            );
        }

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
                        Toast.makeText(MainActivity.this, "Đang chuyển bài tiếp theo", Toast.LENGTH_SHORT).show();
                    } else {
                        MusicPlayerManager.getInstance().playPrevious();
                        Toast.makeText(MainActivity.this, "Đang quay lại bài trước", Toast.LENGTH_SHORT).show();
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

        // MiniPlayer Play/Pause Toggle thuc te
        binding.includedMiniPlayer.btnMiniPlayPause.setOnClickListener(v -> {
            MusicPlayerManager.getInstance().togglePlayPause();
        });

        // Favorite Toggle thuc te qua API
        binding.includedMiniPlayer.btnMiniFavorite.setOnClickListener(v -> {
            toggleFavorite();
        });
    }

    private void openFullscreenPlayer() {
        Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
        intent.putExtra("SONG_TITLE", MusicPlayerManager.getInstance().getCurrentTitle());
        intent.putExtra("ARTIST_NAME", MusicPlayerManager.getInstance().getCurrentArtist());
        intent.putExtra("COVER_URL", MusicPlayerManager.getInstance().getCurrentCoverUrl());
        intent.putExtra("AUDIO_URL", MusicPlayerManager.getInstance().getCurrentAudioUrl());
        intent.putExtra("SONG_ID", MusicPlayerManager.getInstance().getCurrentSongId());
        intent.putExtra("IS_LIKED", MusicPlayerManager.getInstance().isCurrentLiked());
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_bottom, R.anim.stay);
    }

    private void toggleFavorite() {
        String songId = MusicPlayerManager.getInstance().getCurrentSongId();
        if (songId == null || songId.isEmpty()) {
            isFavorite = !isFavorite;
            updateFavoriteIcon(isFavorite);
            MusicPlayerManager.getInstance().setCurrentSongLiked(isFavorite);
            return;
        }

        boolean nextLiked = !isFavorite;
        updateFavoriteIcon(nextLiked);

        ApiClient.getApiService().toggleFavorite(songId).enqueue(new Callback<ApiResponse<ToggleFavoriteData>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<ToggleFavoriteData>> call, @NonNull Response<ApiResponse<ToggleFavoriteData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    boolean serverLiked = response.body().getData().isLiked();
                    isFavorite = serverLiked;
                    updateFavoriteIcon(isFavorite);
                    MusicPlayerManager.getInstance().setCurrentSongLiked(isFavorite);
                    String msg = isFavorite ? "Đã thêm vào bài hát yêu thích" : "Đã xóa khỏi bài hát yêu thích";
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                } else {
                    updateFavoriteIcon(isFavorite);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<ToggleFavoriteData>> call, @NonNull Throwable t) {
                updateFavoriteIcon(isFavorite);
            }
        });
    }

    private void updateFavoriteIcon(boolean liked) {
        binding.includedMiniPlayer.btnMiniFavorite.setImageResource(
                liked ? R.drawable.ic_favorite_filled : R.drawable.ic_favorite_border
        );
        int tintColor = liked ? getColor(R.color.spotify_green) : getColor(R.color.text_secondary);
        binding.includedMiniPlayer.btnMiniFavorite.setColorFilter(tintColor);
    }

    @Override
    public void onTrackChanged(String title, String artist, String coverUrl) {
        runOnUiThread(() -> {
            binding.includedMiniPlayer.miniPlayerRoot.setVisibility(View.VISIBLE);
            binding.includedMiniPlayer.tvMiniSongTitle.setText(title);
            binding.includedMiniPlayer.tvMiniArtistName.setText(artist);

            Glide.with(this)
                    .load(coverUrl)
                    .placeholder(R.drawable.bg_card_rounded)
                    .error(R.drawable.bg_card_rounded)
                    .into(binding.includedMiniPlayer.ivMiniAlbumArt);
        });
    }

    @Override
    public void onFavoriteStateChanged(String songId, boolean isLiked) {
        runOnUiThread(() -> {
            this.isFavorite = isLiked;
            updateFavoriteIcon(isLiked);
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
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    public void updateMiniPlayerVisibility(boolean isVisible) {
        binding.includedMiniPlayer.miniPlayerRoot.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }

    public void updateMiniPlayerTrack(Song song) {
        if (song == null) return;
        MusicPlayerManager.getInstance().playSong(song);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        MusicPlayerManager.getInstance().removeListener(this);
    }
}
