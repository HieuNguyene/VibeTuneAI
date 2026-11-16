package com.example.spotifyclone.presentation.player;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.spotifyclone.R;
import com.example.spotifyclone.data.model.Album;
import com.example.spotifyclone.data.model.ApiResponse;
import com.example.spotifyclone.data.model.Artist;
import com.example.spotifyclone.data.model.ArtistDetailData;
import com.example.spotifyclone.data.model.Playlist;
import com.example.spotifyclone.data.model.Song;
import com.example.spotifyclone.data.model.ToggleFavoriteData;
import com.example.spotifyclone.data.remote.ApiClient;
import com.example.spotifyclone.presentation.artist.ArtistDetailActivity;
import com.google.android.material.button.MaterialButton;
import com.example.spotifyclone.databinding.ActivityPlayerBinding;
import com.example.spotifyclone.databinding.LayoutBottomSheetAddToPlaylistBinding;
import com.example.spotifyclone.databinding.LayoutBottomSheetAlbumInfoBinding;
import com.example.spotifyclone.databinding.LayoutBottomSheetArtistInfoBinding;
import com.example.spotifyclone.databinding.LayoutBottomSheetDeviceConnectBinding;
import com.example.spotifyclone.databinding.LayoutBottomSheetMoodFlowArcBinding;
import com.example.spotifyclone.databinding.LayoutBottomSheetPlaybackSpeedBinding;
import com.example.spotifyclone.databinding.LayoutBottomSheetPlayerMenuBinding;
import com.example.spotifyclone.databinding.LayoutBottomSheetQueueBinding;
import com.example.spotifyclone.databinding.LayoutBottomSheetSimilarSongsBinding;
import com.example.spotifyclone.databinding.LayoutBottomSheetSleepTimerBinding;
import com.example.spotifyclone.player.MusicPlayerManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import com.example.spotifyclone.data.model.LyricLine;
import com.example.spotifyclone.player.LrcParser;
import java.util.Locale;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PlayerActivity extends AppCompatActivity implements MusicPlayerManager.PlayerListener {

    private ActivityPlayerBinding binding;
    private boolean isLiked = false;
    private boolean isUserSeeking = false;

    // Synced Lyrics Engine (LRC & Click-to-Seek)
    private LyricsAdapter lyricsScrollAdapter;
    private List<LyricLine> currentLyricLines = new ArrayList<>();
    private int currentLyricIndex = -1;

    // Bộ định thời 60ms cho Cơ chế Cuộn Mượt & Đổi Màu (Smooth Scroll & Dynamic Highlight)
    private static final long LYRICS_TICK_INTERVAL_MS = 60L;
    private static final long LYRIC_SYNC_LEAD_MS = 180L; // Bù trừ trễ âm thanh & phản xạ thị giác người nghe
    private final Handler lyricsTickerHandler = new Handler(Looper.getMainLooper());
    private boolean isLyricsTickerRunning = false;
    private boolean isUserTouchingLyrics = false;
    private final Handler lyricsTouchHandler = new Handler(Looper.getMainLooper());
    private final Runnable resumeAutoScrollRunnable = () -> isUserTouchingLyrics = false;

    private final Runnable lyricsTickerRunnable = new Runnable() {
        @Override
        public void run() {
            if (MusicPlayerManager.getInstance().isPrepared() && MusicPlayerManager.getInstance().isPlaying()) {
                int currentMs = MusicPlayerManager.getInstance().getCurrentPosition();
                syncLyricsProgress(currentMs);
            }
            if (isLyricsTickerRunning) {
                lyricsTickerHandler.postDelayed(this, LYRICS_TICK_INTERVAL_MS);
            }
        }
    };

    // BottomSheet Dialogs
    private BottomSheetDialog queueBottomSheetDialog;
    private QueueSongAdapter queueSongAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        com.example.spotifyclone.util.ThemeManager.applySystemBarsAppearance(this);

        // Bù trừ an toàn thanh trạng thái trên đỉnh và thanh cử chỉ điều hướng ở đáy
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.playerRootLayout, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars() | androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            );

            android.view.ViewGroup.LayoutParams lp = binding.statusBarSpacerPlayer.getLayoutParams();
            if (lp.height != bars.top) {
                lp.height = bars.top;
                binding.statusBarSpacerPlayer.setLayoutParams(lp);
            }

            binding.playerRootLayout.setPadding(0, 0, 0, bars.bottom);
            return insets;
        });
        if (binding.playerRootLayout.isAttachedToWindow()) {
            androidx.core.view.ViewCompat.requestApplyInsets(binding.playerRootLayout);
        }

        MusicPlayerManager.getInstance().addListener(this);

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });

        extractIntentDataAndPlay();
        setupPlaybackControls();
        setupSeekBar();
        setupLyricsRecyclerViews();
        syncPlayerState();
        updateLyrics(MusicPlayerManager.getInstance().getCurrentSong());

        // Đảm bảo trình phát nhạc luôn mở ở đầu trang (hiển thị ảnh bìa & điều khiển trước)
        binding.scrollPlayerContent.post(() -> binding.scrollPlayerContent.scrollTo(0, 0));
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.scrollPlayerContent.post(() -> binding.scrollPlayerContent.scrollTo(0, 0));
        if (MusicPlayerManager.getInstance().isPrepared() && MusicPlayerManager.getInstance().isPlaying()) {
            startLyricsTicker();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLyricsTicker();
    }

    private void extractIntentDataAndPlay() {
        String songTitle = getIntent().getStringExtra("SONG_TITLE");
        String artistName = getIntent().getStringExtra("ARTIST_NAME");
        String coverUrl = getIntent().getStringExtra("COVER_URL");
        String audioUrl = getIntent().getStringExtra("AUDIO_URL");
        String songId = getIntent().getStringExtra("SONG_ID");
        boolean intentLiked = getIntent().getBooleanExtra("IS_LIKED", false);

        if (songTitle != null && !songTitle.isEmpty()) binding.tvPlayerSongTitle.setText(songTitle);
        if (artistName != null && !artistName.isEmpty()) binding.tvPlayerArtistName.setText(artistName);

        // LATCH CHECK: If this audio track is already preparing or playing, DO NOT reset player
        String currentPlayingUrl = MusicPlayerManager.getInstance().getCurrentAudioUrl();
        if (audioUrl != null && !audioUrl.isEmpty()) {
            if (!audioUrl.equals(currentPlayingUrl)) {
                // Different song: trigger playback
                MusicPlayerManager.getInstance().playSong(songTitle, artistName, coverUrl, audioUrl);
            } else {
                // Same song: ensure playback is running if paused
                if (!MusicPlayerManager.getInstance().isPlaying() && MusicPlayerManager.getInstance().isPrepared()) {
                    MusicPlayerManager.getInstance().togglePlayPause();
                }
            }

            if (songId != null && !songId.isEmpty()) {
                isLiked = intentLiked;
                MusicPlayerManager.getInstance().setCurrentSongLiked(isLiked);
            }
            loadCoverAndPalette(coverUrl);
        } else if (!MusicPlayerManager.getInstance().getCurrentTitle().isEmpty()) {
            // No intent URL provided, sync from singleton
            binding.tvPlayerSongTitle.setText(MusicPlayerManager.getInstance().getCurrentTitle());
            binding.tvPlayerArtistName.setText(MusicPlayerManager.getInstance().getCurrentArtist());
            loadCoverAndPalette(MusicPlayerManager.getInstance().getCurrentCoverUrl());
            isLiked = MusicPlayerManager.getInstance().isCurrentLiked();
        }

        updateFavoriteIcon(isLiked);
        updatePlayPauseButton(MusicPlayerManager.getInstance().isPlaying());
    }

    private void syncPlayerState() {
        updateShuffleButton(MusicPlayerManager.getInstance().isShuffle());
        updateRepeatButton(MusicPlayerManager.getInstance().getRepeatMode());
        updatePlayPauseButton(MusicPlayerManager.getInstance().isPlaying());
        updatePlaybackSpeedUI(MusicPlayerManager.getInstance().getPlaybackSpeed());
        updateMoodFlowUI(
                MusicPlayerManager.getInstance().isMoodFlowMode(),
                MusicPlayerManager.getInstance().getCurrentArc(),
                MusicPlayerManager.getInstance().getCurrentReason()
        );
        if (MusicPlayerManager.getInstance().isPrepared()) {
            int currentMs = MusicPlayerManager.getInstance().getCurrentPosition();
            int totalMs = MusicPlayerManager.getInstance().getDuration();
            if (totalMs > 0) {
                binding.playerSeekBar.setMax(totalMs);
                binding.playerSeekBar.setProgress(currentMs);
                binding.tvCurrentTimestamp.setText(formatTime(currentMs));
                binding.tvTotalDuration.setText(formatTime(totalMs));
            }
        }
    }

    private void loadCoverAndPalette(String coverUrl) {
        if (coverUrl == null || coverUrl.isEmpty()) {
            applyDynamicGradient(0xFF1A365D);
            return;
        }

        Glide.with(this)
                .asBitmap()
                .load(coverUrl)
                .placeholder(R.drawable.bg_card_rounded)
                .error(R.drawable.bg_card_rounded)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        if (isFinishing() || isDestroyed() || binding == null) {
                            return;
                        }
                        binding.ivPlayerAlbumArt.setImageBitmap(resource);
                        Palette.from(resource).generate(palette -> {
                            if (palette != null && !isFinishing() && !isDestroyed() && binding != null) {
                                int dominantColor = palette.getDominantColor(getColor(R.color.spotify_elevated));
                                applyDynamicGradient(dominantColor);
                            }
                        });
                    }

                    @Override
                    public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {}
                });
    }

    private void applyDynamicGradient(int topColor) {
        int bottomColor = getColor(R.color.spotify_black);
        boolean isNight = com.example.spotifyclone.util.ThemeManager.isNightMode(this);
        double lum = androidx.core.graphics.ColorUtils.calculateLuminance(topColor);
        int resolvedTop;
        int midColor;
        if (isNight) {
            float topBlend = lum > 0.25 ? 0.52f : 0.12f;
            resolvedTop = androidx.core.graphics.ColorUtils.blendARGB(topColor, bottomColor, topBlend);
            midColor = androidx.core.graphics.ColorUtils.blendARGB(topColor, bottomColor, 0.76f);
        } else {
            float topBlend = lum < 0.08 ? 0.68f : 0.56f;
            resolvedTop = androidx.core.graphics.ColorUtils.blendARGB(topColor, bottomColor, topBlend);
            midColor = androidx.core.graphics.ColorUtils.blendARGB(topColor, bottomColor, 0.88f);
        }
        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{resolvedTop, midColor, bottomColor}
        );
        gradient.setCornerRadius(0f);
        binding.vGradientOverlay.setBackground(gradient);
    }

    private void setupSeekBar() {
        binding.playerSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    binding.tvCurrentTimestamp.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
                MusicPlayerManager.getInstance().seekTo(seekBar.getProgress());
                isUserTouchingLyrics = false;
                syncLyricsProgress(seekBar.getProgress());
            }
        });
    }

    private void setupPlaybackControls() {
        // 1. Collapse Player
        binding.btnCollapsePlayer.setOnClickListener(v -> finish());

        // Tap artist name to open artist profile screen
        binding.tvPlayerArtistName.setOnClickListener(v -> navigateToArtistDetail());

        // 2. Play / Pause Toggle
        com.example.spotifyclone.ui.liquid.LiquidDropletHelper.attachDropletSpring(
                binding.btnPlayPause,
                binding.btnMoodFlowArcSelector
        );
        binding.btnPlayPause.setOnClickListener(v -> {
            MusicPlayerManager.getInstance().togglePlayPause();
        });

        // 3. Next Track
        binding.btnNext.setOnClickListener(v -> {
            MusicPlayerManager.getInstance().playNext();
        });

        // 4. Previous Track
        binding.btnPrevious.setOnClickListener(v -> {
            MusicPlayerManager.getInstance().playPrevious();
        });

        // 4b. Rewind -10s / Forward +30s
        binding.btnRewind10s.setOnClickListener(v -> {
            MusicPlayerManager.getInstance().seekBy(-10_000);
        });

        binding.btnForward30s.setOnClickListener(v -> {
            MusicPlayerManager.getInstance().seekBy(30_000);
        });

        // 4c. Playback Speed Pill (0.5x..2.0x, default 1.0x)
        updatePlaybackSpeedUI(MusicPlayerManager.getInstance().getPlaybackSpeed());
        binding.btnPlaybackSpeed.setOnClickListener(v -> {
            showPlaybackSpeedBottomSheet();
        });

        // 5. Favorite Toggle with API Call
        binding.btnPlayerFavorite.setOnClickListener(v -> {
            toggleFavorite();
        });

        // 5b. Dislike Track Button
        binding.btnPlayerDislike.setOnClickListener(v -> {
            MusicPlayerManager.getInstance().dislikeCurrentTrack();
            Toast.makeText(this, "Đã bỏ qua bài hát này", Toast.LENGTH_SHORT).show();
        });

        // 5c. Mood Flow Arc Selector
        binding.btnMoodFlowArcSelector.setOnClickListener(v -> {
            showMoodFlowArcBottomSheet();
        });

        // 6. Shuffle Toggle
        binding.btnShuffle.setOnClickListener(v -> {
            boolean nextShuffle = !MusicPlayerManager.getInstance().isShuffle();
            MusicPlayerManager.getInstance().setShuffle(nextShuffle);
            updateShuffleButton(nextShuffle);
            String msg = nextShuffle ? "Đã bật phát ngẫu nhiên" : "Đã tắt phát ngẫu nhiên";
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });

        // 7. Repeat Toggle (3-state cycle: OFF -> ALL -> ONE -> OFF)
        binding.btnRepeat.setOnClickListener(v -> {
            int currentMode = MusicPlayerManager.getInstance().getRepeatMode();
            int nextMode;
            if (currentMode == MusicPlayerManager.REPEAT_OFF) {
                nextMode = MusicPlayerManager.REPEAT_ALL;
                Toast.makeText(this, "Lặp lại toàn bộ danh sách", Toast.LENGTH_SHORT).show();
            } else if (currentMode == MusicPlayerManager.REPEAT_ALL) {
                nextMode = MusicPlayerManager.REPEAT_ONE;
                Toast.makeText(this, "Lặp lại bài hát này", Toast.LENGTH_SHORT).show();
            } else {
                nextMode = MusicPlayerManager.REPEAT_OFF;
                Toast.makeText(this, "Tắt lặp lại", Toast.LENGTH_SHORT).show();
            }
            MusicPlayerManager.getInstance().setRepeatMode(nextMode);
            updateRepeatButton(nextMode);
        });

        // 8. Queue BottomSheet
        binding.btnQueueList.setOnClickListener(v -> showQueueBottomSheet());

        // 9. Device Connect Dialog
        binding.btnDeviceConnect.setOnClickListener(v -> showDeviceConnectDialog());

        // 10. Options Menu BottomSheet
        binding.btnPlayerMenu.setOnClickListener(v -> showPlayerMenuBottomSheet());

        // 11. Lyrics Peek Bar (Tự động cuộn xuống khung lời bài hát ở phía dưới)
        binding.layoutLyricsPeek.setOnClickListener(v -> {
            showBottomLyrics();
        });

        // 12. Nút cuộn lên đầu từ phần Lời bài hát
        binding.btnScrollBackToTop.setOnClickListener(v -> {
            binding.scrollPlayerContent.smoothScrollTo(0, 0);
        });

        // 13. Thiết lập tương tác phần Lời bài hát phía dưới
        setupLyricsGestures();
    }

    private void setupLyricsGestures() {
        // 1. Chạm vào khung lời bài hát bên dưới để cuộn mượt mà xem trọn vẹn
        binding.cardLyricsScrollSection.setOnClickListener(v -> {
            binding.scrollPlayerContent.smoothScrollTo(0, binding.cardLyricsScrollSection.getTop());
        });

        // 2. Lắng nghe sự kiện cuộn trang (khi người dùng cuộn xuống dưới, tự động nạp lời bài hát)
        binding.scrollPlayerContent.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (scrollY > 50) {
                Song song = MusicPlayerManager.getInstance().getCurrentSong();
                updateLyrics(song);
            }
        });
    }

    private void showBottomLyrics() {
        Song song = MusicPlayerManager.getInstance().getCurrentSong();
        updateLyrics(song);
        binding.scrollPlayerContent.post(() -> {
            binding.scrollPlayerContent.smoothScrollTo(0, binding.cardLyricsScrollSection.getTop());
        });
    }

    private void setupLyricsRecyclerViews() {
        lyricsScrollAdapter = new LyricsAdapter((line, position) -> {
            if (line != null && line.getTimestampMs() >= 0) {
                MusicPlayerManager.getInstance().seekTo((int) line.getTimestampMs());
                isUserTouchingLyrics = false;
                syncLyricsProgress((int) line.getTimestampMs());
                smoothScrollLyricToCenter(position);
            }
        });
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        binding.rvLyricsScrollSection.setLayoutManager(layoutManager);
        binding.rvLyricsScrollSection.setAdapter(lyricsScrollAdapter);

        // Lắng nghe thao tác vuốt / cuộn của người dùng để tạm hoãn cuộn tự động
        binding.rvLyricsScrollSection.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    isUserTouchingLyrics = true;
                    lyricsTouchHandler.removeCallbacks(resumeAutoScrollRunnable);
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    lyricsTouchHandler.removeCallbacks(resumeAutoScrollRunnable);
                    lyricsTouchHandler.postDelayed(resumeAutoScrollRunnable, 2500L);
                }
            }
        });
    }

    private String getRichLyrics(Song song) {
        if (song != null && song.getLyrics() != null && !song.getLyrics().trim().isEmpty()) {
            return song.getLyrics().trim();
        }

        String title = (song != null && song.getTitle() != null) ? song.getTitle().toLowerCase(Locale.ROOT) : "";

        if (title.contains("vùng an toàn") || title.contains("vung an toan")) {
            return "[00:02.00]Mm-hm, mm-hm\n" +
                   "[00:06.50]Mm-hm\n" +
                   "[00:11.00]Oh\n" +
                   "[00:17.50]Yeah\n" +
                   "[00:25.52]Và anh lại nghĩ về anh là ai ngày hôm nay,\n" +
                   "[00:28.30]Những khoảnh khắc, luôn dẫn dắt cho anh được quay lại cơn say,\n" +
                   "[00:31.14]Sai lầm nhất là khi mà ta đã nghĩ rằng ngày mai là hôm nay,\n" +
                   "[00:34.12]Nhưng mà anh đã chọn âm nhạc, So I guess I'm okay.\n" +
                   "[00:36.85]Vì hôm đó, khó để có thể nào hình dung,\n" +
                   "[00:39.72]Nếu như anh đã không bỏ đi, chỉ để chứng minh là mình đúng,\n" +
                   "[00:42.70]Không tinh tướng, không nhịn, không nhường, không phình trướng,\n" +
                   "[00:45.37]Không hình tượng, em biết rõ em chọn yêu gã không bình thường,\n" +
                   "[00:48.32]\"Nếu mày yêu nó, mày sẽ không bao giờ có thể cất bước,\n" +
                   "[00:51.42]Chưa biết chắc mày có được gì, nhưng cái gia đình là mày mất trước,\n" +
                   "[00:54.25]Trên con đường đuổi lấy danh vọng, thường sẽ không có ngày về,\n" +
                   "[00:56.89]Mà đó là nếu và chỉ là nếu mày còn có nơi để mày về,\n" +
                   "[00:59.75]Nếu lời nói mày không thể giữ, thì còn giá trị gì lời thề?\"\n" +
                   "[01:02.45]So here I am, cả chai rượu chứa đầy nước mắt của thằng hề.\n" +
                   "[01:05.37]Anh vẫn là Bảo, luôn làm lỗi và tự mình dày vò,\n" +
                   "[01:08.29]Nhưng anh sẽ không thay đổi điều gì nếu như có quay lại ngày đó. I loved you.\n" +
                   "[01:12.00]Và cho tới lúc cuối cùng chẳng có ai còn lại nơi đây,\n" +
                   "[01:15.54]While nothing lasts I know that everything’s gon’ be okay,\n" +
                   "[01:21.22]Em sẽ không còn giữ khi anh cứ buông ra, những lời nói tổn thương,\n" +
                   "[01:27.95]Còn lại một chút gì đó vấn vương,\n" +
                   "[01:30.90]Bông hoa giờ vơi phần ngát hương.\n" +
                   "[01:33.79]Được và mất, anh không chắc là mình sẽ thắng khi anh bước đi,\n" +
                   "[01:39.20]Thôi thì ta trắng tay,\n" +
                   "[01:42.07]Cho nhau 1 kết thúc Hollywood style vì chiếu lâu sẽ mất hay,\n" +
                   "[01:47.52]Everything’s gon’ be okay,\n" +
                   "[01:52.84]Một chương mới đang sang trang khi hai ta phải bước ra vùng an toàn.\n" +
                   "[01:57.77]Sự thật là, vẫn chưa ai yêu anh nhiều hơn em từng làm,\n" +
                   "[02:00.07]Và cũng sẽ chẳng có ai có thể khiến cho nước mắt của em rơi từng hàng,\n" +
                   "[02:03.00]Và kể cả về sau này, có trong tay biển bạc và rừng vàng,\n" +
                   "[02:05.87]Cũng không bao giờ có thể thay thế người con gái bên cạnh lúc bần hàn,\n" +
                   "[02:08.59]Nhưng mà đây không phải lời thú tội,\n" +
                   "[02:10.25]Anh không tới để nhận án,\n" +
                   "[02:11.62]Anh đã có hình phạt, đó là nhìn em bên người xứng đáng,\n" +
                   "[02:14.27]Ta vẫn yêu và quan tâm nhau, chỉ là dùng một cách tiếp cận khác,\n" +
                   "[02:17.40]Tiếc là ta gặp nhau vào những năm tháng anh vẫn chưa sẵn sàng,\n" +
                   "[02:20.22]chưa sẵn sàng để đối mặt,\n" +
                   "[02:21.62]chưa sẵn sàng để vấp ngã,\n" +
                   "[02:23.00]chưa sẵn sàng để buông tha,\n" +
                   "[02:24.47]chưa sẵn sàng cho tất cả,\n" +
                   "[02:25.75]Giờ hơi thở anh đầy mùi cồn,\n" +
                   "[02:26.97]Và cổ áo anh toàn mùi thuốc lá,\n" +
                   "[02:28.65]Anh chỉ để lại đống đổ nát tất cả những nơi mà anh từng bước qua,\n" +
                   "[02:32.25]Anh, luôn luôn có một tâm niệm lớn,\n" +
                   "[02:34.00]Rằng anh sẽ lớn lên khác ba mình, nhưng rồi sau đó anh chưa hề lớn,\n" +
                   "[02:37.17]Em có nhớ lúc khi bên nhau, anh luôn nói với em là gì?\n" +
                   "[02:39.67]Anh sẽ làm tất cả để em hạnh phúc, kể cả là việc anh phải ra đi,\n" +
                   "[02:44.44]Và cho tới lúc cuối cùng chẳng có ai còn lại nơi đây,\n" +
                   "[02:49.77]While nothing lasts I know that everything’s gon’ be okay,\n" +
                   "[02:55.17]Em sẽ không còn giữ khi anh cứ buông ra, những lời nói tổn thương,\n" +
                   "[03:02.25]Còn lại một chút gì đó vấn vương,\n" +
                   "[03:05.17]Bông hoa giờ vơi phần ngát hương.\n" +
                   "[03:08.22]Được và mất, anh không chắc là mình sẽ thắng khi anh bước đi,\n" +
                   "[03:13.44]Thôi thì ta trắng tay,\n" +
                   "[03:16.22]Cho nhau một kết thúc Hollywood style vì chiếu lâu sẽ mất hay,\n" +
                   "[03:21.62]Everything’s gon’ be okay,\n" +
                   "[03:26.90]Một chương mới đang sang trang khi hai ta phải bước ra vùng an toàn.\n" +
                   "[03:34.52](Everything’s gon’ be okay,\n" +
                   "[03:37.47]Everything’s gon’ be okay,\n" +
                   "[03:42.92]Everything’s gon’ be okay,\n" +
                   "[03:51.55]Everything’s gon’ be okay)\n" +
                   "[04:20.57]So, kể cho em nghe đi, từ đầu là như thế nào?";
        } else if (title.contains("exit sign")) {
            return "[00:23.05](Hey, Kewtiie)\n" +
                   "[00:24.64]Anh không nhớ nổi lần cuối cùng anh nhìn vào mắt em đó là từ bao giờ\n" +
                   "[00:27.55]Em từng trách anh chỉ ôm ước mơ, còn không sợ mất em thì làm sao chờ?\n" +
                   "[00:30.77]Lúc đó cả hai đứa đều không thể nào nhún nhường\n" +
                   "[00:34.22]Cứ tưởng tình yêu là một chuyến xe buýt, có thể đổi chuyến ở trạm dừng chân\n" +
                   "[00:39.50]Nhưng mà không em ơi...";
        } else if (title.contains("tìm em") || title.contains("tim em")) {
            return "[00:23.14]Một ngày vắng yêu thương nỗi nhớ quay cuồng trong đêm\n" +
                   "[00:28.18]Nhiều ngày vắng hỡi em nỗi nhớ ngày càng dâng lên\n" +
                   "[00:34.25]Tóc rối khi không thấy người, đẫm nát lang thang cuối trời\n" +
                   "[00:40.08]Ngày tàn anh chơi vơi với cuộc đời...";
        } else if (title.contains("chẳng thể tìm") || title.contains("chang the tim")) {
            return "[00:19.80]Anh dừng chân, ngước nhìn trời cao vẫn muôn ánh sao\n" +
                   "[00:27.89]Để buồn đau, phủ lên bờ vai hao gầy\n" +
                   "[00:35.72]Ngày mà ta rời xa, quay lưng về nhau\n" +
                   "[00:41.18]Liệu đã có ai ngoảnh lại...";
        } else {
            return "[00:15.00]♪ Giai điệu mở đầu vang lên êm dịu ♪\n" +
                   "[00:22.00]Khi cơn mưa vừa rơi qua lối nhỏ\n" +
                   "[00:29.00]Anh bồi hồi nhớ lại tháng ngày qua\n" +
                   "[00:36.00]Từng giai điệu ngân vang trong lòng ta\n" +
                   "[00:44.00]Ấm áp từng giây phút dịu êm\n" +
                   "[00:52.00]Hòa mình vào không gian âm nhạc\n" +
                   "[01:00.00]Xua tan đi mọi âu lo mệt nhoài\n" +
                   "[01:10.00]Cảm nhận từng cung bậc cảm xúc thăng hoa.";
        }
    }

    private void updateLyrics(Song song) {
        String rawLyrics = getRichLyrics(song);
        long durationMs = (song != null && song.getDuration() > 0) ? song.getDuration() * 1000L : 0L;
        currentLyricLines = LrcParser.parse(rawLyrics, durationMs);
        currentLyricIndex = -2; // Reset trạng thái để nhận diện đoạn intro (-1) một cách chuẩn xác

        if (lyricsScrollAdapter != null) {
            lyricsScrollAdapter.setLyricLines(currentLyricLines);
            scrollLyricsToTop();
        }
        syncLyricsProgress(MusicPlayerManager.getInstance().getCurrentPosition());
    }

    private void syncLyricsProgress(int currentMs) {
        if (currentLyricLines == null || currentLyricLines.isEmpty() || lyricsScrollAdapter == null) {
            return;
        }
        // Bù trừ độ trễ giải mã âm thanh và phản xạ thính giác/thị giác (+180ms)
        long lookupMs = Math.max(0, currentMs + LYRIC_SYNC_LEAD_MS);
        int activeIndex = LrcParser.findActiveLineIndex(currentLyricLines, lookupMs);
        if (activeIndex != currentLyricIndex) {
            currentLyricIndex = activeIndex;
            boolean changed = lyricsScrollAdapter.setActivePosition(activeIndex);
            if (binding != null && binding.tvLyricsStatusBadge != null) {
                binding.tvLyricsStatusBadge.setVisibility(activeIndex < 0 ? View.VISIBLE : View.GONE);
            }
            if (changed && !isUserTouchingLyrics) {
                if (activeIndex >= 0) {
                    smoothScrollLyricToCenter(activeIndex);
                } else {
                    // Đang ở đoạn nhạc dạo đầu (intro) chưa có lời: giữ yên danh sách ở đầu trang
                    scrollLyricsToTop();
                }
            }
        }
    }

    private void scrollLyricsToTop() {
        if (binding == null || binding.rvLyricsScrollSection == null) return;
        RecyclerView.LayoutManager layoutManager = binding.rvLyricsScrollSection.getLayoutManager();
        if (layoutManager instanceof LinearLayoutManager) {
            ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(0, 0);
        }
    }

    private void smoothScrollLyricToCenter(int position) {
        if (position < 0 || lyricsScrollAdapter == null || position >= lyricsScrollAdapter.getItemCount()) {
            return;
        }
        RecyclerView.LayoutManager layoutManager = binding.rvLyricsScrollSection.getLayoutManager();
        if (!(layoutManager instanceof LinearLayoutManager)) return;

        LinearSmoothScroller scroller = new LinearSmoothScroller(this) {
            @Override
            public int calculateDtToFit(int viewStart, int viewEnd, int boxStart, int boxEnd, int snapPreference) {
                // Căn chính xác tâm của dòng lời bài hát (view) trùng với tâm của khung nhìn RecyclerView (box)
                int boxMid = boxStart + (boxEnd - boxStart) / 2;
                int viewMid = viewStart + (viewEnd - viewStart) / 2;
                return boxMid - viewMid;
            }

            @Override
            protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
                // Tốc độ lướt mượt mà, uyển chuyển (~35ms / inch)
                return 35f / (displayMetrics != null ? displayMetrics.densityDpi : DisplayMetrics.DENSITY_DEFAULT);
            }

            @Override
            protected int calculateTimeForScrolling(int dx) {
                // Giới hạn thời gian cuộn tối đa 350ms để bắt kịp nhịp bài hát nhanh
                return Math.min(350, super.calculateTimeForScrolling(dx));
            }

            @Override
            protected void onTargetFound(View targetView, RecyclerView.State state, Action action) {
                int dy = calculateDyToMakeVisible(targetView, getVerticalSnapPreference());
                if (dy == 0) return;
                int time = Math.max(250, Math.min(calculateTimeForDeceleration(Math.abs(dy)), 400));
                action.update(0, -dy, time, new android.view.animation.DecelerateInterpolator(1.6f));
            }
        };
        scroller.setTargetPosition(position);
        layoutManager.startSmoothScroll(scroller);
    }

    private void startLyricsTicker() {
        if (isLyricsTickerRunning) return;
        isLyricsTickerRunning = true;
        lyricsTickerHandler.removeCallbacks(lyricsTickerRunnable);
        lyricsTickerHandler.post(lyricsTickerRunnable);
    }

    private void stopLyricsTicker() {
        isLyricsTickerRunning = false;
        lyricsTickerHandler.removeCallbacks(lyricsTickerRunnable);
    }

    private void updateShuffleButton(boolean isShuffle) {
        binding.btnShuffle.setColorFilter(
                isShuffle ? getColor(R.color.spotify_green) : getColor(R.color.text_secondary)
        );
    }

    private void updateRepeatButton(int mode) {
        if (mode == MusicPlayerManager.REPEAT_ONE) {
            binding.btnRepeat.setImageResource(R.drawable.ic_repeat_one);
            binding.btnRepeat.setColorFilter(getColor(R.color.spotify_green));
        } else if (mode == MusicPlayerManager.REPEAT_ALL) {
            binding.btnRepeat.setImageResource(R.drawable.ic_repeat);
            binding.btnRepeat.setColorFilter(getColor(R.color.spotify_green));
        } else {
            binding.btnRepeat.setImageResource(R.drawable.ic_repeat);
            binding.btnRepeat.setColorFilter(getColor(R.color.text_secondary));
        }
    }

    private void showQueueBottomSheet() {
        queueBottomSheetDialog = new BottomSheetDialog(this, R.style.Theme_Spotify_BottomSheet);
        LayoutBottomSheetQueueBinding queueBinding = LayoutBottomSheetQueueBinding.inflate(getLayoutInflater());
        queueBottomSheetDialog.setContentView(queueBinding.getRoot());

        List<Song> queue = MusicPlayerManager.getInstance().getCurrentQueue();
        int activeIndex = MusicPlayerManager.getInstance().getCurrentIndex();

        queueBinding.tvQueueTrackCount.setText(queue.size() + " bài hát");

        queueSongAdapter = new QueueSongAdapter((song, position) -> {
            MusicPlayerManager.getInstance().playTrackAtIndex(position);
            queueBottomSheetDialog.dismiss();
        });

        queueBinding.rvQueueTracks.setLayoutManager(new LinearLayoutManager(this));
        queueBinding.rvQueueTracks.setAdapter(queueSongAdapter);
        queueSongAdapter.setQueue(queue, activeIndex);

        if (activeIndex >= 0 && activeIndex < queue.size()) {
            queueBinding.rvQueueTracks.scrollToPosition(activeIndex);
        }

        queueBottomSheetDialog.show();
    }

    private void showDeviceConnectDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_Spotify_BottomSheet);
        LayoutBottomSheetDeviceConnectBinding deviceBinding = LayoutBottomSheetDeviceConnectBinding.inflate(getLayoutInflater());
        dialog.setContentView(deviceBinding.getRoot());

        deviceBinding.btnConnectBluetooth.setOnClickListener(v -> {
            dialog.dismiss();
            try {
                startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "Không thể mở Cài đặt Bluetooth trên thiết bị này", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void showPlayerMenuBottomSheet() {
        BottomSheetDialog menuDialog = new BottomSheetDialog(this, R.style.Theme_Spotify_BottomSheet);
        LayoutBottomSheetPlayerMenuBinding menuBinding = LayoutBottomSheetPlayerMenuBinding.inflate(getLayoutInflater());
        menuDialog.setContentView(menuBinding.getRoot());

        String title = MusicPlayerManager.getInstance().getCurrentTitle();
        String artist = MusicPlayerManager.getInstance().getCurrentArtist();
        String coverUrl = MusicPlayerManager.getInstance().getCurrentCoverUrl();
        Song currentSong = MusicPlayerManager.getInstance().getCurrentSong();
        String songId = MusicPlayerManager.getInstance().getCurrentSongId();

        menuBinding.tvMenuSongTitle.setText(title != null && !title.isEmpty() ? title : "Đang phát");
        String displayArtist = artist != null && !artist.isEmpty() ? artist : "Nghệ sĩ VibeTune";
        if (currentSong != null && currentSong.getComposer() != null && !currentSong.getComposer().isEmpty() && !currentSong.getComposer().equalsIgnoreCase(artist)) {
            displayArtist += " • Sáng tác: " + currentSong.getComposer();
        }
        menuBinding.tvMenuArtistName.setText(displayArtist);

        Glide.with(this)
                .load(coverUrl)
                .placeholder(R.drawable.bg_card_rounded)
                .error(R.drawable.bg_card_rounded)
                .into(menuBinding.ivMenuSongArt);

        // 0. Khám phá bài hát tương tự (Song Radio)
        menuBinding.itemMenuSimilarSongs.setOnClickListener(v -> {
            menuDialog.dismiss();
            showSimilarSongsBottomSheet(songId, title);
        });

        // 1. Chia sẻ
        menuBinding.itemMenuShare.setOnClickListener(v -> {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Nghe nhạc trên VibeTune");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Đang nghe bài hát \"" + title + "\" của " + artist + ".");
            startActivity(Intent.createChooser(shareIntent, "Chia sẻ bài hát qua"));
            menuDialog.dismiss();
        });

        // 2. Lời bài hát ở phía dưới
        boolean isLyricsShowing = binding.scrollPlayerContent.getScrollY() > 100;
        menuBinding.tvLyricsToggleState.setText(isLyricsShowing ? "Đang xem • Nhấn để lên đầu" : "Xem lời bài hát ở phía dưới");
        menuBinding.itemMenuLyrics.setOnClickListener(v -> {
            if (binding.scrollPlayerContent.getScrollY() > 100) {
                binding.scrollPlayerContent.smoothScrollTo(0, 0);
                Toast.makeText(this, "Đã cuộn lên đầu", Toast.LENGTH_SHORT).show();
            } else {
                showBottomLyrics();
                Toast.makeText(this, "Đã mở lời bài hát ở phía dưới", Toast.LENGTH_SHORT).show();
            }
            menuDialog.dismiss();
        });

        // 3. Thêm vào danh sách yêu thích
        menuBinding.ivMenuFavoriteIcon.setImageResource(
                isLiked ? R.drawable.ic_favorite_filled : R.drawable.ic_favorite_border
        );
        menuBinding.ivMenuFavoriteIcon.setColorFilter(
                isLiked ? getColor(R.color.spotify_green) : getColor(R.color.text_primary)
        );
        menuBinding.tvMenuFavoriteText.setText(
                isLiked ? "Xóa khỏi danh sách yêu thích" : "Thêm vào danh sách yêu thích"
        );
        menuBinding.itemMenuFavorite.setOnClickListener(v -> {
            toggleFavorite();
            menuDialog.dismiss();
        });

        // 4. Thêm vào danh sách phát
        menuBinding.itemMenuAddToPlaylist.setOnClickListener(v -> {
            menuDialog.dismiss();
            showAddToPlaylistDialog();
        });

        // 5. Ẩn trong danh sách phát
        menuBinding.itemMenuHideSong.setOnClickListener(v -> {
            MusicPlayerManager.getInstance().hideCurrentSongFromQueue();
            Toast.makeText(this, "Đã ẩn bài hát khỏi danh sách phát", Toast.LENGTH_SHORT).show();
            menuDialog.dismiss();
        });

        // 6. Thêm vào danh sách chờ
        menuBinding.itemMenuAddToQueue.setOnClickListener(v -> {
            if (currentSong != null) {
                MusicPlayerManager.getInstance().addToQueue(currentSong);
            }
            Toast.makeText(this, "Đã thêm vào danh sách chờ", Toast.LENGTH_SHORT).show();
            menuDialog.dismiss();
        });

        // 8. Chuyển đến album
        menuBinding.itemMenuGoToAlbum.setOnClickListener(v -> {
            menuDialog.dismiss();
            showAlbumDialog();
        });

        // 9. Chuyển tới trang nghệ sĩ
        menuBinding.itemMenuGoToArtist.setOnClickListener(v -> {
            menuDialog.dismiss();
            navigateToArtistDetail();
        });

        // 10. Tốc độ phát
        float currentSpeed = MusicPlayerManager.getInstance().getPlaybackSpeed();
        menuBinding.tvMenuPlaybackSpeedSummary.setText(formatSpeedSummary(currentSpeed));
        menuBinding.itemMenuPlaybackSpeed.setOnClickListener(v -> {
            menuDialog.dismiss();
            showPlaybackSpeedBottomSheet();
        });

        // 11. Hẹn giờ tắt
        boolean isTimerActive = MusicPlayerManager.getInstance().isSleepTimerActive();
        String timerDesc = MusicPlayerManager.getInstance().getSleepTimerDescription();
        menuBinding.tvSleepTimerStatusSummary.setText(timerDesc);
        if (isTimerActive) {
            menuBinding.ivSleepTimerIcon.setColorFilter(getColor(R.color.spotify_green));
            menuBinding.tvSleepTimerStatusSummary.setTextColor(getColor(R.color.spotify_green));
        }
        menuBinding.itemMenuSleepTimer.setOnClickListener(v -> {
            menuDialog.dismiss();
            showSleepTimerDialog();
        });

        menuDialog.show();
    }

    private void showSimilarSongsBottomSheet(String songId, String title) {
        if (songId == null || songId.isEmpty()) {
            Toast.makeText(this, "Không có thông tin bài hát hiện tại", Toast.LENGTH_SHORT).show();
            return;
        }

        BottomSheetDialog similarDialog = new BottomSheetDialog(this, R.style.Theme_Spotify_BottomSheet);
        LayoutBottomSheetSimilarSongsBinding similarBinding = LayoutBottomSheetSimilarSongsBinding.inflate(getLayoutInflater());
        similarDialog.setContentView(similarBinding.getRoot());

        similarBinding.tvSimilarTargetSubtitle.setText("Dựa trên đặc trưng âm nhạc của: " + (title != null ? title : "bài hát này"));
        similarBinding.pbSimilarLoading.setVisibility(View.VISIBLE);
        similarBinding.rvSimilarTracks.setVisibility(View.GONE);
        similarBinding.tvSimilarEmpty.setVisibility(View.GONE);
        similarBinding.btnPlayAllSimilar.setVisibility(View.GONE);

        SimilarSongAdapter adapter = new SimilarSongAdapter((song, list, position) -> {
            MusicPlayerManager.getInstance().playQueue(list, position);
            Toast.makeText(this, "Đang phát: " + song.getTitle(), Toast.LENGTH_SHORT).show();
            similarDialog.dismiss();
        });

        similarBinding.rvSimilarTracks.setLayoutManager(new LinearLayoutManager(this));
        similarBinding.rvSimilarTracks.setAdapter(adapter);

        ApiClient.getApiService().getSimilarSongs(songId).enqueue(new Callback<ApiResponse<List<Song>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<Song>>> call, @NonNull Response<ApiResponse<List<Song>>> response) {
                if (isFinishing() || isDestroyed()) return;
                similarBinding.pbSimilarLoading.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    List<Song> similarSongs = response.body().getData();
                    if (!similarSongs.isEmpty()) {
                        adapter.setSongs(similarSongs);
                        similarBinding.rvSimilarTracks.setVisibility(View.VISIBLE);
                        similarBinding.btnPlayAllSimilar.setVisibility(View.VISIBLE);
                        similarBinding.btnPlayAllSimilar.setOnClickListener(v -> {
                            MusicPlayerManager.getInstance().playQueue(similarSongs, 0);
                            Toast.makeText(PlayerActivity.this, "Đang phát danh sách bài hát tương tự", Toast.LENGTH_SHORT).show();
                            similarDialog.dismiss();
                        });
                    } else {
                        similarBinding.tvSimilarEmpty.setVisibility(View.VISIBLE);
                        similarBinding.tvSimilarEmpty.setText("Chưa tìm thấy bài hát tương tự phù hợp.");
                    }
                } else {
                    similarBinding.tvSimilarEmpty.setVisibility(View.VISIBLE);
                    similarBinding.tvSimilarEmpty.setText("Không thể tải danh sách bài hát tương tự.");
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<Song>>> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                similarBinding.pbSimilarLoading.setVisibility(View.GONE);
                similarBinding.tvSimilarEmpty.setVisibility(View.VISIBLE);
                similarBinding.tvSimilarEmpty.setText("Lỗi kết nối khi tải gợi ý âm nhạc.");
            }
        });

        similarDialog.show();
    }

    private void showSleepTimerDialog() {
        BottomSheetDialog timerDialog = new BottomSheetDialog(this, R.style.Theme_Spotify_BottomSheet);
        LayoutBottomSheetSleepTimerBinding timerBinding = LayoutBottomSheetSleepTimerBinding.inflate(getLayoutInflater());
        timerDialog.setContentView(timerBinding.getRoot());

        boolean isActive = MusicPlayerManager.getInstance().isSleepTimerActive();
        if (isActive) {
            timerBinding.tvSleepTimerStatus.setText("Đang hẹn giờ: " + MusicPlayerManager.getInstance().getSleepTimerDescription());
            timerBinding.tvSleepTimerStatus.setTextColor(getColor(R.color.spotify_green));
            timerBinding.btnTimerTurnOff.setVisibility(View.VISIBLE);
        } else {
            timerBinding.tvSleepTimerStatus.setText("Dừng phát nhạc tự động sau khoảng thời gian chọn");
            timerBinding.btnTimerTurnOff.setVisibility(View.GONE);
        }

        timerBinding.btnTimer5Min.setOnClickListener(v -> {
            MusicPlayerManager.getInstance().startSleepTimer(5);
            Toast.makeText(this, "Hẹn giờ tắt nhạc sau 5 phút", Toast.LENGTH_SHORT).show();
            timerDialog.dismiss();
        });

        timerBinding.btnTimer15Min.setOnClickListener(v -> {
            MusicPlayerManager.getInstance().startSleepTimer(15);
            Toast.makeText(this, "Hẹn giờ tắt nhạc sau 15 phút", Toast.LENGTH_SHORT).show();
            timerDialog.dismiss();
        });

        timerBinding.btnTimer30Min.setOnClickListener(v -> {
            MusicPlayerManager.getInstance().startSleepTimer(30);
            Toast.makeText(this, "Hẹn giờ tắt nhạc sau 30 phút", Toast.LENGTH_SHORT).show();
            timerDialog.dismiss();
        });

        timerBinding.btnTimer45Min.setOnClickListener(v -> {
            MusicPlayerManager.getInstance().startSleepTimer(45);
            Toast.makeText(this, "Hẹn giờ tắt nhạc sau 45 phút", Toast.LENGTH_SHORT).show();
            timerDialog.dismiss();
        });

        timerBinding.btnTimer60Min.setOnClickListener(v -> {
            MusicPlayerManager.getInstance().startSleepTimer(60);
            Toast.makeText(this, "Hẹn giờ tắt nhạc sau 1 giờ", Toast.LENGTH_SHORT).show();
            timerDialog.dismiss();
        });

        timerBinding.btnTimerEndOfSong.setOnClickListener(v -> {
            MusicPlayerManager.getInstance().setSleepTimerAtEndOfSong(true);
            Toast.makeText(this, "Sẽ tắt sau khi kết thúc bài hát này", Toast.LENGTH_SHORT).show();
            timerDialog.dismiss();
        });

        timerBinding.btnTimerTurnOff.setOnClickListener(v -> {
            MusicPlayerManager.getInstance().cancelSleepTimer();
            Toast.makeText(this, "Đã tắt tính năng hẹn giờ", Toast.LENGTH_SHORT).show();
            timerDialog.dismiss();
        });

        timerDialog.show();
    }

    private void showAlbumDialog() {
        BottomSheetDialog albumDialog = new BottomSheetDialog(this, R.style.Theme_Spotify_BottomSheet);
        LayoutBottomSheetAlbumInfoBinding albumBinding = LayoutBottomSheetAlbumInfoBinding.inflate(getLayoutInflater());
        albumDialog.setContentView(albumBinding.getRoot());

        Song currentSong = MusicPlayerManager.getInstance().getCurrentSong();
        Album albumObj = currentSong != null ? currentSong.getAlbum() : null;
        String fallbackTitle = MusicPlayerManager.getInstance().getCurrentTitle();
        String fallbackArtist = MusicPlayerManager.getInstance().getCurrentArtist();
        String fallbackCover = MusicPlayerManager.getInstance().getCurrentCoverUrl();

        String initialTitle = (albumObj != null && !albumObj.getTitle().isEmpty())
                ? albumObj.getTitle()
                : (fallbackTitle != null && !fallbackTitle.isEmpty() ? fallbackTitle : "Đĩa đơn");
        String initialArtist = (albumObj != null && !albumObj.getArtistName().isEmpty())
                ? albumObj.getArtistName()
                : (fallbackArtist != null && !fallbackArtist.isEmpty() ? fallbackArtist : "VibeTune");

        albumBinding.tvAlbumTitle.setText(initialTitle);
        albumBinding.tvAlbumArtist.setText(initialArtist);
        albumBinding.tvAlbumMeta.setText(albumObj != null && albumObj.getReleaseYear() > 0
                ? albumObj.getReleaseYear() + " • Đang tải thông tin album..."
                : "Đĩa đơn • Nhạc gốc từ máy chủ VibeTune");
        albumBinding.btnPlayAlbum.setText(albumObj != null && !albumObj.getId().isEmpty()
                ? "Phát toàn bộ album"
                : "Phát bài hát");

        Glide.with(this)
                .load(fallbackCover)
                .placeholder(R.drawable.bg_card_rounded)
                .error(R.drawable.bg_card_rounded)
                .into(albumBinding.ivAlbumCover);

        @SuppressWarnings("unchecked")
        final List<Song>[] albumTracksHolder = new List[]{null};

        if (albumObj != null && !albumObj.getId().isEmpty()) {
            ApiClient.getApiService().getAlbumDetails(albumObj.getId()).enqueue(new Callback<ApiResponse<Album>>() {
                @Override
                public void onResponse(@NonNull Call<ApiResponse<Album>> call,
                                       @NonNull Response<ApiResponse<Album>> response) {
                    if (isFinishing() || isDestroyed() || !albumDialog.isShowing()) return;
                    if (!response.isSuccessful() || response.body() == null || response.body().getData() == null) {
                        albumBinding.tvAlbumMeta.setText("Đĩa đơn • Nhạc gốc từ máy chủ VibeTune");
                        return;
                    }
                    Album fetched = response.body().getData();
                    if (!fetched.getTitle().isEmpty()) {
                        albumBinding.tvAlbumTitle.setText(fetched.getTitle());
                    }
                    if (!fetched.getArtistName().isEmpty()) {
                        albumBinding.tvAlbumArtist.setText(fetched.getArtistName());
                    }
                    String yearPrefix = fetched.getReleaseYear() > 0 ? fetched.getReleaseYear() + " • " : "";
                    int totalTracks = fetched.getTotalTracks();
                    albumBinding.tvAlbumMeta.setText(yearPrefix + totalTracks + " bài hát • Bản gốc máy chủ");

                    List<Song> tracks = fetched.getTracks();
                    if (tracks != null && !tracks.isEmpty()) {
                        albumTracksHolder[0] = tracks;
                        albumBinding.btnPlayAlbum.setText("Phát toàn bộ album (" + tracks.size() + " bài)");
                    }

                    String resolvedCover = !fetched.getCoverImageUrl().isEmpty()
                            ? ApiClient.resolveMediaUrl(fetched.getCoverImageUrl())
                            : fallbackCover;
                    Glide.with(PlayerActivity.this)
                            .load(resolvedCover)
                            .placeholder(R.drawable.bg_card_rounded)
                            .error(R.drawable.bg_card_rounded)
                            .into(albumBinding.ivAlbumCover);
                }

                @Override
                public void onFailure(@NonNull Call<ApiResponse<Album>> call, @NonNull Throwable t) {
                    if (isFinishing() || isDestroyed() || !albumDialog.isShowing()) return;
                    albumBinding.tvAlbumMeta.setText("Không thể tải chi tiết album");
                }
            });
        }

        albumBinding.btnPlayAlbum.setOnClickListener(v -> {
            List<Song> tracks = albumTracksHolder[0];
            CharSequence displayTitle = albumBinding.tvAlbumTitle.getText();
            if (tracks != null && !tracks.isEmpty()) {
                MusicPlayerManager.getInstance().playQueue(tracks, 0);
                Toast.makeText(this, "Đang phát album " + displayTitle, Toast.LENGTH_SHORT).show();
            } else if (currentSong != null) {
                MusicPlayerManager.getInstance().playSong(currentSong);
                Toast.makeText(this, "Đang phát " + displayTitle, Toast.LENGTH_SHORT).show();
            } else {
                MusicPlayerManager.getInstance().seekTo(0);
            }
            albumDialog.dismiss();
        });

        albumDialog.show();
    }

    private void showArtistDialog() {
        BottomSheetDialog artistDialog = new BottomSheetDialog(this, R.style.Theme_Spotify_BottomSheet);
        LayoutBottomSheetArtistInfoBinding artistBinding = LayoutBottomSheetArtistInfoBinding.inflate(getLayoutInflater());
        artistDialog.setContentView(artistBinding.getRoot());

        Song song = MusicPlayerManager.getInstance().getCurrentSong();
        Artist artistObj = song != null ? song.getArtist() : null;
        String artistId = artistObj != null ? artistObj.getId() : null;
        String rawArtistName = MusicPlayerManager.getInstance().getCurrentArtist();
        final String artistName = rawArtistName != null && !rawArtistName.isEmpty() ? rawArtistName : "Nghệ sĩ VibeTune";

        String avatarUrl = (artistObj != null && artistObj.getAvatarUrl() != null && !artistObj.getAvatarUrl().isEmpty())
                ? ApiClient.resolveMediaUrl(artistObj.getAvatarUrl())
                : MusicPlayerManager.getInstance().getCurrentCoverUrl();

        artistBinding.tvArtistName.setText(artistName);
        if (artistObj != null && artistObj.getFollowersCount() > 0) {
            artistBinding.tvArtistFollowers.setText(String.format(Locale.getDefault(), "%,d người theo dõi", artistObj.getFollowersCount()));
        } else {
            artistBinding.tvArtistFollowers.setText(artistId != null && !artistId.isEmpty()
                    ? "Đang tải thông tin nghệ sĩ..."
                    : "Nghệ sĩ trên VibeTune");
        }

        if (artistObj != null && artistObj.getBio() != null && !artistObj.getBio().isEmpty()) {
            artistBinding.tvArtistBio.setText(artistObj.getBio());
        } else {
            artistBinding.tvArtistBio.setText("Nghệ sĩ đang hoạt động trên hệ thống VibeTune.");
        }

        Glide.with(this)
                .load(avatarUrl)
                .placeholder(R.drawable.bg_card_rounded)
                .error(R.drawable.bg_card_rounded)
                .into(artistBinding.ivArtistAvatar);

        final boolean[] isFollowingHolder = new boolean[]{artistObj != null && artistObj.isFollowed()};
        bindArtistFollowButton(artistBinding.btnFollowArtist, isFollowingHolder[0]);

        if (artistId != null && !artistId.isEmpty()) {
            ApiClient.getApiService().getSongsByArtist(artistId).enqueue(new Callback<ApiResponse<ArtistDetailData>>() {
                @Override
                public void onResponse(@NonNull Call<ApiResponse<ArtistDetailData>> call,
                                       @NonNull Response<ApiResponse<ArtistDetailData>> response) {
                    if (isFinishing() || isDestroyed() || !artistDialog.isShowing()) return;
                    if (!response.isSuccessful() || response.body() == null || response.body().getData() == null) {
                        return;
                    }
                    Artist fetched = response.body().getData().getArtist();
                    if (fetched == null) return;

                    if (fetched.getName() != null && !fetched.getName().isEmpty()) {
                        artistBinding.tvArtistName.setText(fetched.getName());
                    }
                    artistBinding.tvArtistFollowers.setText(String.format(Locale.getDefault(), "%,d người theo dõi", fetched.getFollowersCount()));
                    if (fetched.getBio() != null && !fetched.getBio().isEmpty()) {
                        artistBinding.tvArtistBio.setText(fetched.getBio());
                    }
                    if (fetched.getAvatarUrl() != null && !fetched.getAvatarUrl().isEmpty()) {
                        Glide.with(PlayerActivity.this)
                                .load(ApiClient.resolveMediaUrl(fetched.getAvatarUrl()))
                                .placeholder(R.drawable.bg_card_rounded)
                                .error(R.drawable.bg_card_rounded)
                                .into(artistBinding.ivArtistAvatar);
                    }
                    isFollowingHolder[0] = fetched.isFollowed();
                    bindArtistFollowButton(artistBinding.btnFollowArtist, isFollowingHolder[0]);
                    if (artistObj != null) {
                        artistObj.setFollowed(fetched.isFollowed());
                        artistObj.setFollowersCount(fetched.getFollowersCount());
                    }
                }

                @Override
                public void onFailure(@NonNull Call<ApiResponse<ArtistDetailData>> call, @NonNull Throwable t) {
                    // Keep initial cached info if network refresh fails
                }
            });

            artistBinding.btnFollowArtist.setOnClickListener(v -> {
                artistBinding.btnFollowArtist.setEnabled(false);
                ApiClient.getApiService().toggleFollowArtist(artistId).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse<Map<String, Object>>> call,
                                           @NonNull Response<ApiResponse<Map<String, Object>>> response) {
                        if (isFinishing() || isDestroyed()) return;
                        artistBinding.btnFollowArtist.setEnabled(true);
                        if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                            Map<String, Object> data = response.body().getData();
                            Object followObj = data.get("isFollowed");
                            boolean nextFollowed = followObj instanceof Boolean
                                    ? (Boolean) followObj
                                    : !isFollowingHolder[0];
                            isFollowingHolder[0] = nextFollowed;
                            bindArtistFollowButton(artistBinding.btnFollowArtist, nextFollowed);

                            Object countObj = data.get("followersCount");
                            if (countObj instanceof Number) {
                                long count = ((Number) countObj).longValue();
                                artistBinding.tvArtistFollowers.setText(String.format(Locale.getDefault(), "%,d người theo dõi", count));
                                if (artistObj != null) {
                                    artistObj.setFollowersCount(count);
                                }
                            }
                            if (artistObj != null) {
                                artistObj.setFollowed(nextFollowed);
                            }
                            Toast.makeText(PlayerActivity.this,
                                    (nextFollowed ? "Đã theo dõi " : "Đã bỏ theo dõi ") + artistBinding.tvArtistName.getText(),
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(PlayerActivity.this, "Không thể cập nhật trạng thái theo dõi", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse<Map<String, Object>>> call, @NonNull Throwable t) {
                        if (isFinishing() || isDestroyed()) return;
                        artistBinding.btnFollowArtist.setEnabled(true);
                        Toast.makeText(PlayerActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            });
        } else {
            artistBinding.btnFollowArtist.setEnabled(false);
            artistBinding.btnFollowArtist.setAlpha(0.6f);
            artistBinding.btnFollowArtist.setText("Chưa có hồ sơ nghệ sĩ");
        }

        artistBinding.btnViewAllSongs.setOnClickListener(v -> {
            artistDialog.dismiss();
            navigateToArtistDetail();
        });

        artistDialog.show();
    }

    private void bindArtistFollowButton(MaterialButton button, boolean isFollowing) {
        if (isFollowing) {
            button.setText("Đang theo dõi");
            button.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.spotify_elevated)));
            button.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        } else {
            button.setText("Theo dõi nghệ sĩ");
            button.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.spotify_green)));
            button.setTextColor(ContextCompat.getColor(this, R.color.on_primary_accent));
        }
    }

    private void navigateToArtistDetail() {
        Song song = MusicPlayerManager.getInstance().getCurrentSong();
        Artist artistObj = song != null ? song.getArtist() : null;
        if (artistObj != null && artistObj.getId() != null && !artistObj.getId().isEmpty()) {
            Intent intent = new Intent(this, ArtistDetailActivity.class);
            intent.putExtra(ArtistDetailActivity.EXTRA_ARTIST_ID, artistObj.getId());
            intent.putExtra(ArtistDetailActivity.EXTRA_ARTIST_NAME, artistObj.getName());
            intent.putExtra(ArtistDetailActivity.EXTRA_ARTIST_AVATAR, artistObj.getAvatarUrl());
            intent.putExtra(ArtistDetailActivity.EXTRA_ARTIST_BIO, artistObj.getBio());
            intent.putExtra(ArtistDetailActivity.EXTRA_ARTIST_FOLLOWERS, artistObj.getFollowersCount());
            startActivity(intent);
        } else {
            showArtistDialog();
        }
    }

    private void showAddToPlaylistDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_Spotify_BottomSheet);
        LayoutBottomSheetAddToPlaylistBinding playlistBinding = LayoutBottomSheetAddToPlaylistBinding.inflate(getLayoutInflater());
        dialog.setContentView(playlistBinding.getRoot());

        String songId = MusicPlayerManager.getInstance().getCurrentSongId();

        playlistBinding.btnCreateNewPlaylist.setOnClickListener(v -> {
            dialog.dismiss();
            SongOptionsBottomSheetDialog.openCreatePlaylistAndAddSong(this, songId);
        });

        playlistBinding.pbLoadingPlaylists.setVisibility(View.VISIBLE);
        playlistBinding.rvUserPlaylists.setLayoutManager(new LinearLayoutManager(this));

        ApiClient.getApiService().getMyPlaylists().enqueue(new Callback<ApiResponse<List<Playlist>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<Playlist>>> call, @NonNull Response<ApiResponse<List<Playlist>>> response) {
                playlistBinding.pbLoadingPlaylists.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null && !response.body().getData().isEmpty()) {
                    List<Playlist> playlists = response.body().getData();
                    PlaylistPickerAdapter adapter = new PlaylistPickerAdapter(playlists, playlist -> {
                        SongOptionsBottomSheetDialog.addSongToPlaylist(PlayerActivity.this, playlist.getId(), playlist.getTitle(), songId);
                        dialog.dismiss();
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

        dialog.show();
    }

    private void toggleFavorite() {
        String songId = MusicPlayerManager.getInstance().getCurrentSongId();
        if (songId == null || songId.isEmpty()) {
            isLiked = !isLiked;
            updateFavoriteIcon(isLiked);
            MusicPlayerManager.getInstance().setCurrentSongLiked(isLiked);
            animateFavoriteButton();
            return;
        }

        boolean nextLiked = !isLiked;
        updateFavoriteIcon(nextLiked);
        animateFavoriteButton();

        ApiClient.getApiService().toggleFavorite(songId).enqueue(new Callback<ApiResponse<ToggleFavoriteData>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<ToggleFavoriteData>> call, @NonNull Response<ApiResponse<ToggleFavoriteData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    boolean serverLiked = response.body().getData().isLiked();
                    isLiked = serverLiked;
                    updateFavoriteIcon(isLiked);
                    MusicPlayerManager.getInstance().setCurrentSongLiked(isLiked);
                    String msg = isLiked ? "Đã thêm vào Bài hát đã thích" : "Đã xóa khỏi Bài hát đã thích";
                    Toast.makeText(PlayerActivity.this, msg, Toast.LENGTH_SHORT).show();
                } else {
                    updateFavoriteIcon(isLiked);
                    Toast.makeText(PlayerActivity.this, "Không thể cập nhật yêu thích", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<ToggleFavoriteData>> call, @NonNull Throwable t) {
                updateFavoriteIcon(isLiked);
                Toast.makeText(PlayerActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateFavoriteIcon(boolean liked) {
        binding.btnPlayerFavorite.setImageResource(
                liked ? R.drawable.ic_favorite_filled : R.drawable.ic_favorite_border
        );
        int color = liked ? getColor(R.color.spotify_green) : getColor(R.color.text_secondary);
        binding.btnPlayerFavorite.setColorFilter(color);
    }

    private void animateFavoriteButton() {
        binding.btnPlayerFavorite.animate()
                .scaleX(1.25f).scaleY(1.25f)
                .setDuration(120)
                .withEndAction(() -> binding.btnPlayerFavorite.animate().scaleX(1f).scaleY(1f).setDuration(120).start())
                .start();
    }

    private void updatePlayPauseButton(boolean isPlaying) {
        binding.btnPlayPause.setImageResource(
                isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow
        );
    }

    @Override
    public void onTrackChanged(String title, String artist, String coverUrl) {
        runOnUiThread(() -> {
            binding.tvPlayerSongTitle.setText(title);
            binding.tvPlayerArtistName.setText(artist);
            loadCoverAndPalette(coverUrl);
            updateLyrics(MusicPlayerManager.getInstance().getCurrentSong());
        });
    }

    @Override
    public void onSongChanged(Song song) {
        if (song == null) return;
        runOnUiThread(() -> {
            isLiked = song.isLiked();
            updateFavoriteIcon(isLiked);
            updateLyrics(song);
        });
    }

    @Override
    public void onFavoriteStateChanged(String songId, boolean isLiked) {
        runOnUiThread(() -> {
            this.isLiked = isLiked;
            updateFavoriteIcon(isLiked);
        });
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        runOnUiThread(() -> {
            updatePlayPauseButton(isPlaying);
            if (isPlaying) {
                startLyricsTicker();
            } else {
                stopLyricsTicker();
            }
        });
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        runOnUiThread(() -> updateRepeatButton(repeatMode));
    }

    @Override
    public void onShuffleModeChanged(boolean isShuffle) {
        runOnUiThread(() -> updateShuffleButton(isShuffle));
    }

    @Override
    public void onQueueChanged(List<Song> queue, int currentIndex) {
        runOnUiThread(() -> {
            if (queueSongAdapter != null) {
                queueSongAdapter.setQueue(queue, currentIndex);
            }
        });
    }

    @Override
    public void onProgressUpdate(int currentMs, int totalMs) {
        if (isUserSeeking) return;

        runOnUiThread(() -> {
            if (totalMs > 0) {
                binding.playerSeekBar.setMax(totalMs);
                binding.playerSeekBar.setProgress(currentMs);
                binding.tvCurrentTimestamp.setText(formatTime(currentMs));
                binding.tvTotalDuration.setText(formatTime(totalMs));
            }
            syncLyricsProgress(currentMs);
        });
    }

    @Override
    public void onError(String message) {
        runOnUiThread(() -> Toast.makeText(PlayerActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onPlaybackSpeedChanged(float speed) {
        runOnUiThread(() -> updatePlaybackSpeedUI(speed));
    }

    private void updatePlaybackSpeedUI(float speed) {
        if (binding == null) return;
        float normalized = MusicPlayerManager.normalizeSpeedValue(speed);
        String rateLabel = formatSpeedShort(normalized);
        binding.tvPlaybackSpeed.setText("Tốc độ " + rateLabel);
        boolean isCustom = Math.abs(normalized - MusicPlayerManager.DEFAULT_PLAYBACK_SPEED) >= 0.01f;
        binding.btnPlaybackSpeed.setBackgroundResource(isCustom ? R.drawable.bg_pill_active : R.drawable.bg_pill_inactive);
        binding.tvPlaybackSpeed.setTextColor(ContextCompat.getColor(
                this,
                isCustom ? R.color.on_primary_accent : R.color.text_primary
        ));
        binding.ivPlaybackSpeedIcon.setColorFilter(ContextCompat.getColor(
                this,
                isCustom ? R.color.on_primary_accent : R.color.text_secondary
        ));
    }

    private String formatSpeedShort(float speed) {
        if (Math.abs(speed - 0.75f) < 0.01f) return "0.75x";
        if (Math.abs(speed - 1.25f) < 0.01f) return "1.25x";
        return String.format(Locale.US, "%.1fx", speed);
    }

    private String formatSpeedSummary(float speed) {
        String shortLabel = formatSpeedShort(MusicPlayerManager.normalizeSpeedValue(speed));
        return "1.0x".equals(shortLabel) ? "1.0x (Chuẩn)" : shortLabel;
    }

    private void showPlaybackSpeedBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_Spotify_BottomSheet);
        LayoutBottomSheetPlaybackSpeedBinding speedBinding = LayoutBottomSheetPlaybackSpeedBinding.inflate(getLayoutInflater());
        dialog.setContentView(speedBinding.getRoot());

        float current = MusicPlayerManager.normalizeSpeedValue(MusicPlayerManager.getInstance().getPlaybackSpeed());
        speedBinding.ivCheckSpeed05.setVisibility(Math.abs(current - 0.5f) < 0.01f ? View.VISIBLE : View.GONE);
        speedBinding.ivCheckSpeed075.setVisibility(Math.abs(current - 0.75f) < 0.01f ? View.VISIBLE : View.GONE);
        speedBinding.ivCheckSpeed10.setVisibility(Math.abs(current - 1.0f) < 0.01f ? View.VISIBLE : View.GONE);
        speedBinding.ivCheckSpeed125.setVisibility(Math.abs(current - 1.25f) < 0.01f ? View.VISIBLE : View.GONE);
        speedBinding.ivCheckSpeed15.setVisibility(Math.abs(current - 1.5f) < 0.01f ? View.VISIBLE : View.GONE);
        speedBinding.ivCheckSpeed20.setVisibility(Math.abs(current - 2.0f) < 0.01f ? View.VISIBLE : View.GONE);

        bindSpeedOption(speedBinding.itemSpeed05, 0.5f, dialog);
        bindSpeedOption(speedBinding.itemSpeed075, 0.75f, dialog);
        bindSpeedOption(speedBinding.itemSpeed10, 1.0f, dialog);
        bindSpeedOption(speedBinding.itemSpeed125, 1.25f, dialog);
        bindSpeedOption(speedBinding.itemSpeed15, 1.5f, dialog);
        bindSpeedOption(speedBinding.itemSpeed20, 2.0f, dialog);

        dialog.show();
    }

    private void bindSpeedOption(View itemView, float speed, BottomSheetDialog dialog) {
        itemView.setOnClickListener(v -> {
            MusicPlayerManager.getInstance().setPlaybackSpeed(speed);
            Toast.makeText(this, "Tốc độ phát: " + formatSpeedSummary(speed), Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
    }

    @Override
    public void onMoodFlowChanged(boolean isActive, String arc, String reason) {
        runOnUiThread(() -> updateMoodFlowUI(isActive, arc, reason));
    }

    private void updateMoodFlowUI(boolean isActive, String arc, String reason) {
        if (isActive) {
            String arcLabel = "Mood Flow • Ổn định";
            if ("wind_down".equals(arc)) arcLabel = "Mood Flow • Thư giãn";
            else if ("workout".equals(arc)) arcLabel = "Mood Flow • Tập luyện";
            else if ("focus".equals(arc)) arcLabel = "Mood Flow • Tập trung";
            else if ("steady".equals(arc)) arcLabel = "Mood Flow • Ổn định";

            binding.tvMoodFlowArcName.setText(arcLabel);
            binding.tvMoodFlowArcName.setTextColor(ContextCompat.getColor(this, R.color.spotify_green));
            binding.ivMoodFlowDot.setColorFilter(ContextCompat.getColor(this, R.color.spotify_green));
            binding.btnMoodFlowArcSelector.setVisibility(View.VISIBLE);

            if (reason != null && !reason.trim().isEmpty()) {
                binding.layoutMoodFlowReason.setVisibility(View.VISIBLE);
                binding.tvMoodFlowReason.setText(reason.trim());
            } else {
                binding.layoutMoodFlowReason.setVisibility(View.GONE);
            }
        } else {
            binding.tvMoodFlowArcName.setText("Bật Mood Flow");
            binding.tvMoodFlowArcName.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            binding.ivMoodFlowDot.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary));
            binding.btnMoodFlowArcSelector.setVisibility(View.VISIBLE);
            binding.layoutMoodFlowReason.setVisibility(View.GONE);
        }
    }

    private void showMoodFlowArcBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_Spotify_BottomSheet);
        LayoutBottomSheetMoodFlowArcBinding sheetBinding = LayoutBottomSheetMoodFlowArcBinding.inflate(getLayoutInflater());
        dialog.setContentView(sheetBinding.getRoot());

        sheetBinding.btnArcWindDown.setOnClickListener(v -> {
            applyArcChoice("wind_down", "Thư giãn (Wind Down)");
            dialog.dismiss();
        });

        sheetBinding.btnArcWorkout.setOnClickListener(v -> {
            applyArcChoice("workout", "Tập luyện (Workout)");
            dialog.dismiss();
        });

        sheetBinding.btnArcFocus.setOnClickListener(v -> {
            applyArcChoice("focus", "Tập trung (Focus)");
            dialog.dismiss();
        });

        sheetBinding.btnArcSteady.setOnClickListener(v -> {
            applyArcChoice("steady", "Ổn định (Steady)");
            dialog.dismiss();
        });

        sheetBinding.btnArcTurnOff.setOnClickListener(v -> {
            MusicPlayerManager.getInstance().stopMoodFlow();
            Toast.makeText(this, "Đã tắt Mood Flow", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void applyArcChoice(String arc, String arcName) {
        MusicPlayerManager pm = MusicPlayerManager.getInstance();
        if (!pm.isMoodFlowMode()) {
            pm.startMoodFlow(arc);
            Toast.makeText(this, "Đã bật Mood Flow: " + arcName, Toast.LENGTH_SHORT).show();
        } else {
            pm.changeArc(arc);
            Toast.makeText(this, "Đã chuyển lộ trình: " + arcName, Toast.LENGTH_SHORT).show();
        }
    }

    private String formatTime(int ms) {
        int totalSec = Math.max(0, ms / 1000);
        int seconds = totalSec % 60;
        int minutes = totalSec / 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.stay, R.anim.slide_out_bottom);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLyricsTicker();
        lyricsTouchHandler.removeCallbacksAndMessages(null);
        MusicPlayerManager.getInstance().removeListener(this);
        if (queueBottomSheetDialog != null && queueBottomSheetDialog.isShowing()) {
            queueBottomSheetDialog.dismiss();
        }
    }
}
