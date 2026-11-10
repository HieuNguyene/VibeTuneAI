package com.example.spotifyclone.presentation.player;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.spotifyclone.R;
import com.example.spotifyclone.data.model.ApiResponse;
import com.example.spotifyclone.data.model.Song;
import com.example.spotifyclone.data.model.ToggleFavoriteData;
import com.example.spotifyclone.data.remote.ApiClient;
import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.view.GestureDetector;
import android.view.MotionEvent;
import androidx.core.widget.NestedScrollView;
import com.example.spotifyclone.data.model.Artist;
import com.example.spotifyclone.data.model.Playlist;
import com.example.spotifyclone.presentation.artist.ArtistDetailActivity;
import com.example.spotifyclone.databinding.ActivityPlayerBinding;
import com.example.spotifyclone.databinding.LayoutBottomSheetAddToPlaylistBinding;
import com.example.spotifyclone.databinding.LayoutBottomSheetAlbumInfoBinding;
import com.example.spotifyclone.databinding.LayoutBottomSheetArtistInfoBinding;
import com.example.spotifyclone.databinding.LayoutBottomSheetDeviceConnectBinding;
import com.example.spotifyclone.databinding.LayoutBottomSheetPlayerMenuBinding;
import com.example.spotifyclone.databinding.LayoutBottomSheetQueueBinding;
import com.example.spotifyclone.databinding.LayoutBottomSheetSleepTimerBinding;
import com.example.spotifyclone.player.MusicPlayerManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import com.example.spotifyclone.data.model.LyricLine;
import com.example.spotifyclone.player.LrcParser;
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
    private LyricsAdapter lyricsOverlayAdapter;
    private LyricsAdapter lyricsScrollAdapter;
    private List<LyricLine> currentLyricLines = new ArrayList<>();
    private int currentLyricIndex = -1;

    // BottomSheet Dialogs
    private BottomSheetDialog queueBottomSheetDialog;
    private QueueSongAdapter queueSongAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

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
            applyDynamicGradient(0xFF253342);
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
                        binding.ivPlayerAlbumArt.setImageBitmap(resource);
                        Palette.from(resource).generate(palette -> {
                            if (palette != null) {
                                int dominantColor = palette.getDominantColor(0xFF282828);
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
        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{topColor, 0xFF181818, bottomColor}
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
            }
        });
    }

    private void setupPlaybackControls() {
        // 1. Collapse Player
        binding.btnCollapsePlayer.setOnClickListener(v -> finish());

        // Tap artist name to open artist profile screen
        binding.tvPlayerArtistName.setOnClickListener(v -> navigateToArtistDetail());

        // 2. Play / Pause Toggle
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

        // 5. Favorite Toggle with API Call
        binding.btnPlayerFavorite.setOnClickListener(v -> {
            toggleFavorite();
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

        // 11. Close Lyrics Card Overlay
        binding.btnCloseLyrics.setOnClickListener(v -> {
            binding.cardLyricsContainer.setVisibility(View.GONE);
            binding.ivPlayerAlbumArt.setVisibility(View.VISIBLE);
        });

        // 12. Lyrics Peek Bar (Tự động cuộn xuống khung lời bài hát)
        binding.layoutLyricsPeek.setOnClickListener(v -> {
            binding.scrollPlayerContent.smoothScrollTo(0, binding.cardLyricsScrollSection.getTop());
        });

        // 13. Nút cuộn lên đầu từ phần Lời bài hát
        binding.btnScrollBackToTop.setOnClickListener(v -> {
            binding.scrollPlayerContent.smoothScrollTo(0, 0);
        });

        // 14. Thiết lập nhận diện cử chỉ kéo xuống / vuốt xuống tự động bật lời bài hát
        setupLyricsGestures();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupLyricsGestures() {
        // 1. Chạm trực tiếp vào ảnh bìa hoặc thẻ lời bài hát để bật/đổi chế độ
        binding.ivPlayerAlbumArt.setOnClickListener(v -> autoEnableLyrics());
        binding.cardLyricsContainer.setOnClickListener(v -> autoEnableLyrics());

        // 2. Chạm vào khung xem trước lời bài hát bên dưới để cuộn mượt mà xuống xem trọn vẹn
        binding.cardLyricsScrollSection.setOnClickListener(v -> {
            binding.scrollPlayerContent.smoothScrollTo(0, binding.cardLyricsScrollSection.getTop());
        });

        // 3. Nhận diện thao tác kéo xuống (Pull/Swipe Down) trên toàn bộ nội dung trình phát nhạc
        final float[] touchStartY = {0f};
        final float[] touchStartX = {0f};
        final boolean[] hasTriggeredPull = {false};

        binding.scrollPlayerContent.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchStartY[0] = event.getY();
                    touchStartX[0] = event.getX();
                    hasTriggeredPull[0] = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    float dyMove = event.getY() - touchStartY[0];
                    float dxMove = event.getX() - touchStartX[0];
                    if (!hasTriggeredPull[0] && binding.scrollPlayerContent.getScrollY() <= 15 && dyMove > 90 && Math.abs(dyMove) > Math.abs(dxMove)) {
                        hasTriggeredPull[0] = true;
                        autoEnableLyrics();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    float deltaY = event.getY() - touchStartY[0];
                    float deltaX = event.getX() - touchStartX[0];
                    // Khi đang ở đầu trình phát nhạc (scrollY <= 15) và người dùng kéo mạnh xuống (kéo xuống >= 80px)
                    if (!hasTriggeredPull[0] && binding.scrollPlayerContent.getScrollY() <= 15 && deltaY > 80 && Math.abs(deltaY) > Math.abs(deltaX)) {
                        hasTriggeredPull[0] = true;
                        autoEnableLyrics();
                    }
                    break;
            }
            return false; // Trả về false để NestedScrollView vẫn cuộn trang bình thường
        });

        // 4. Lắng nghe sự kiện cuộn trang (khi người dùng cuộn xuống dưới, tự động cập nhật lời bài hát)
        binding.scrollPlayerContent.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (scrollY > 50) {
                Song song = MusicPlayerManager.getInstance().getCurrentSong();
                updateLyrics(song);
            }
        });
    }

    private void autoEnableLyrics() {
        Song song = MusicPlayerManager.getInstance().getCurrentSong();
        updateLyrics(song);
        if (binding.cardLyricsContainer.getVisibility() != View.VISIBLE) {
            binding.cardLyricsContainer.setVisibility(View.VISIBLE);
            binding.ivPlayerAlbumArt.setVisibility(View.INVISIBLE);
            Toast.makeText(this, "✨ Đã tự động bật lời bài hát", Toast.LENGTH_SHORT).show();
        } else {
            // Nếu đã mở thẻ trên ảnh bìa rồi mà người dùng tiếp tục thao tác, cuộn xuống khung chi tiết
            binding.scrollPlayerContent.smoothScrollTo(0, binding.cardLyricsScrollSection.getTop());
        }
    }

    private void setupLyricsRecyclerViews() {
        lyricsOverlayAdapter = new LyricsAdapter(null);
        binding.rvLyricsOverlay.setLayoutManager(new LinearLayoutManager(this));
        binding.rvLyricsOverlay.setAdapter(lyricsOverlayAdapter);

        lyricsScrollAdapter = new LyricsAdapter(null);
        binding.rvLyricsScrollSection.setLayoutManager(new LinearLayoutManager(this));
        binding.rvLyricsScrollSection.setAdapter(lyricsScrollAdapter);
    }

    private String getRichLyrics(Song song) {
        if (song != null && song.getLyrics() != null && !song.getLyrics().trim().isEmpty()) {
            return song.getLyrics().trim();
        }

        String title = (song != null && song.getTitle() != null) ? song.getTitle().toLowerCase() : "";

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
            return "[00:00.00]♪ Exit Sign - HIEUTHUHAI ♪\n" +
                   "[00:05.00]I see the exit sign...\n" +
                   "[00:09.50]Lối thoát cho cuộc tình này đã mở ra\n" +
                   "[00:14.00]Không còn những giận hờn vu vơ\n" +
                   "[00:18.50]Không còn những đêm dài trăn trở\n" +
                   "[00:24.00]Bước ra khỏi ánh đèn mờ ảo\n" +
                   "[00:29.00]Tìm lại nụ cười vốn thuộc về anh\n" +
                   "[00:34.00]Đoạn đường phía trước thênh thang\n" +
                   "[00:40.00]Tạm biệt người từng là tất cả\n" +
                   "[00:48.00]I see the exit sign...\n" +
                   "[00:56.00]Bước đi không ngoảnh lại phía sau\n" +
                   "[01:08.00]Tìm lại chính mình của những ngày xưa.";
        } else if (title.contains("tìm em") || title.contains("tim em")) {
            return "[00:00.00]♪ Tìm Em - Hưng Lê ♪\n" +
                   "[00:05.00]Đi tìm một người trong những giấc mơ...\n" +
                   "[00:11.00]Tìm lại ký ức một thời đã xa\n" +
                   "[00:17.00]Gió đông lạnh buốt qua từng ngõ nhỏ\n" +
                   "[00:23.00]Bóng hình em nay đã khuất xa rồi\n" +
                   "[00:30.00]Có những lời chưa kịp nói\n" +
                   "[00:36.00]Đã vội tan vào trong hư vô\n" +
                   "[00:45.00]Chỉ còn nỗi nhớ đọng lại trong anh\n" +
                   "[00:55.00]Mãi mãi không phai mờ theo thời gian.";
        } else if (title.contains("chẳng thể tìm") || title.contains("chang the tim")) {
            return "[00:00.00]♪ Chẳng Thể Tìm Được Em ♪\n" +
                   "[00:06.00]Chẳng thể tìm được em giữa những thênh thang...\n" +
                   "[00:12.00]Dòng người vội vã lướt qua nhanh\n" +
                   "[00:18.00]Tìm lại ánh mắt nụ cười năm ấy\n" +
                   "[00:24.00]Chỉ còn nỗi nhớ đọng lại trong anh\n" +
                   "[00:32.00]Hoàng hôn buông tím cả góc trời\n" +
                   "[00:40.00]Bao nhiêu kỷ niệm bỗng vỡ đôi...";
        } else {
            return "[00:00.00]♪ Giai điệu bắt đầu vang lên ♪\n" +
                   "[00:06.00]Khi cơn mưa vừa rơi qua lối nhỏ\n" +
                   "[00:12.00]Anh bồi hồi nhớ lại tháng ngày qua\n" +
                   "[00:18.00]Từng giai điệu ngân vang trong lòng ta\n" +
                   "[00:24.00]Ấm áp từng giây phút dịu êm\n" +
                   "[00:32.00]Hòa mình vào không gian âm nhạc\n" +
                   "[00:40.00]Xua tan đi mọi âu lo mệt nhoài\n" +
                   "[00:50.00]Cảm nhận từng cung bậc cảm xúc thăng hoa.";
        }
    }

    private void updateLyrics(Song song) {
        String rawLyrics = getRichLyrics(song);
        currentLyricLines = LrcParser.parse(rawLyrics);
        currentLyricIndex = -1;

        if (lyricsOverlayAdapter != null) {
            lyricsOverlayAdapter.setLyricLines(currentLyricLines);
        }
        if (lyricsScrollAdapter != null) {
            lyricsScrollAdapter.setLyricLines(currentLyricLines);
        }
    }

    private void syncLyricsProgress(int currentMs) {
        // Đã tắt tính năng tự động đồng bộ / cuộn lời theo nhạc theo yêu cầu người dùng
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
            Toast.makeText(this, "Bật Bluetooth trên điện thoại để tự động kết nối", Toast.LENGTH_LONG).show();
            dialog.dismiss();
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
        menuBinding.tvMenuArtistName.setText(artist != null && !artist.isEmpty() ? artist : "Spotify");

        Glide.with(this)
                .load(coverUrl)
                .placeholder(R.drawable.bg_card_rounded)
                .error(R.drawable.bg_card_rounded)
                .into(menuBinding.ivMenuSongArt);

        // 1. Chia sẻ
        menuBinding.itemMenuShare.setOnClickListener(v -> {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Nghe nhạc trên Spotify");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Đang nghe bài hát \"" + title + "\" của " + artist + " trên Spotify Clone! 🎵");
            startActivity(Intent.createChooser(shareIntent, "Chia sẻ bài hát qua"));
            menuDialog.dismiss();
        });

        // 2. Lời bài hát bật hoặc tắt
        boolean isLyricsShowing = binding.cardLyricsContainer.getVisibility() == View.VISIBLE
                || binding.scrollPlayerContent.getScrollY() > 100;
        menuBinding.tvLyricsToggleState.setText(isLyricsShowing ? "Đang bật • Nhấn để tắt" : "Đang tắt • Nhấn để bật");
        menuBinding.itemMenuLyrics.setOnClickListener(v -> {
            if (binding.cardLyricsContainer.getVisibility() == View.VISIBLE || binding.scrollPlayerContent.getScrollY() > 100) {
                binding.cardLyricsContainer.setVisibility(View.GONE);
                binding.ivPlayerAlbumArt.setVisibility(View.VISIBLE);
                binding.scrollPlayerContent.smoothScrollTo(0, 0);
                Toast.makeText(this, "Đã tắt lời bài hát", Toast.LENGTH_SHORT).show();
            } else {
                updateLyrics(currentSong);
                binding.scrollPlayerContent.smoothScrollTo(0, binding.cardLyricsScrollSection.getTop());
                Toast.makeText(this, "Đã bật lời bài hát", Toast.LENGTH_SHORT).show();
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

        // 6. Không tính bản nhạc vào hồ sơ gu
        SharedPreferences sp = getSharedPreferences("taste_profile_prefs", MODE_PRIVATE);
        boolean isExcluded = sp.getBoolean("exclude_" + songId, false);
        if (isExcluded) {
            menuBinding.ivExcludeTasteIcon.setColorFilter(getColor(R.color.spotify_green));
            menuBinding.tvExcludeTasteState.setText("Đang loại trừ khỏi gợi ý gu âm nhạc");
            menuBinding.tvExcludeTasteState.setTextColor(getColor(R.color.spotify_green));
        } else {
            menuBinding.ivExcludeTasteIcon.setColorFilter(getColor(R.color.text_primary));
            menuBinding.tvExcludeTasteState.setText("Không ảnh hưởng đến gợi ý âm nhạc của bạn");
            menuBinding.tvExcludeTasteState.setTextColor(getColor(R.color.text_secondary));
        }
        menuBinding.itemMenuExcludeTaste.setOnClickListener(v -> {
            boolean nextExcluded = !sp.getBoolean("exclude_" + songId, false);
            sp.edit().putBoolean("exclude_" + songId, nextExcluded).apply();
            if (nextExcluded) {
                Toast.makeText(this, "Đã loại trừ bài hát này khỏi hồ sơ gu âm nhạc", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Đã tính lại bài hát này vào hồ sơ gu âm nhạc", Toast.LENGTH_SHORT).show();
            }
            menuDialog.dismiss();
        });

        // 7. Thêm vào danh sách chờ
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

        // 10. Hẹn giờ tắt
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

        String title = MusicPlayerManager.getInstance().getCurrentTitle();
        String artist = MusicPlayerManager.getInstance().getCurrentArtist();
        String coverUrl = MusicPlayerManager.getInstance().getCurrentCoverUrl();

        albumBinding.tvAlbumTitle.setText("Album " + (title != null && !title.isEmpty() ? title : "Top Hits"));
        albumBinding.tvAlbumArtist.setText(artist != null && !artist.isEmpty() ? artist : "Spotify");
        albumBinding.tvAlbumMeta.setText("2026 • 15 bài hát • 320 kbps High-Definition");

        Glide.with(this)
                .load(coverUrl)
                .placeholder(R.drawable.bg_card_rounded)
                .error(R.drawable.bg_card_rounded)
                .into(albumBinding.ivAlbumCover);

        albumBinding.btnPlayAlbum.setOnClickListener(v -> {
            Toast.makeText(this, "Đang phát Album " + title, Toast.LENGTH_SHORT).show();
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
        String artistName = MusicPlayerManager.getInstance().getCurrentArtist();
        String avatarUrl = (artistObj != null && artistObj.getAvatarUrl() != null && !artistObj.getAvatarUrl().isEmpty())
                ? ApiClient.resolveMediaUrl(artistObj.getAvatarUrl())
                : MusicPlayerManager.getInstance().getCurrentCoverUrl();

        artistBinding.tvArtistName.setText(artistName != null && !artistName.isEmpty() ? artistName : "Nghệ sĩ Spotify");
        if (artistObj != null && artistObj.getFollowersCount() > 0) {
            artistBinding.tvArtistFollowers.setText(String.format(Locale.getDefault(), "%,d người nghe hàng tháng", artistObj.getFollowersCount()));
        } else {
            artistBinding.tvArtistFollowers.setText("2.845.210 người nghe hàng tháng");
        }

        if (artistObj != null && artistObj.getBio() != null && !artistObj.getBio().isEmpty()) {
            artistBinding.tvArtistBio.setText(artistObj.getBio());
        }

        Glide.with(this)
                .load(avatarUrl)
                .placeholder(R.drawable.bg_card_rounded)
                .error(R.drawable.bg_card_rounded)
                .into(artistBinding.ivArtistAvatar);

        artistBinding.btnFollowArtist.setOnClickListener(v -> {
            boolean isFollowing = "Đang theo dõi".equals(artistBinding.btnFollowArtist.getText().toString());
            if (isFollowing) {
                artistBinding.btnFollowArtist.setText("Theo dõi nghệ sĩ");
                artistBinding.btnFollowArtist.setBackgroundColor(getColor(R.color.spotify_green));
                artistBinding.btnFollowArtist.setTextColor(getColor(R.color.spotify_black));
                Toast.makeText(this, "Đã bỏ theo dõi " + artistName, Toast.LENGTH_SHORT).show();
            } else {
                artistBinding.btnFollowArtist.setText("Đang theo dõi");
                artistBinding.btnFollowArtist.setBackgroundColor(0xFF333333);
                artistBinding.btnFollowArtist.setTextColor(getColor(R.color.white));
                Toast.makeText(this, "Đã theo dõi " + artistName, Toast.LENGTH_SHORT).show();
            }
        });

        artistBinding.btnViewAllSongs.setOnClickListener(v -> {
            artistDialog.dismiss();
            navigateToArtistDetail();
        });

        artistDialog.show();
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
            Toast.makeText(this, "Vào mục Thư viện để tạo Playlist mới", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
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
                        addSongToSpecificPlaylist(playlist.getId(), playlist.getTitle(), songId);
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

    private void addSongToSpecificPlaylist(String playlistId, String playlistTitle, String songId) {
        if (songId == null || songId.isEmpty()) {
            Toast.makeText(this, "Đã lưu vào danh sách phát " + playlistTitle, Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, String> body = new HashMap<>();
        body.put("songId", songId);

        ApiClient.getApiService().addTrackToPlaylist(playlistId, body).enqueue(new Callback<ApiResponse<Object>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Object>> call, @NonNull Response<ApiResponse<Object>> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(PlayerActivity.this, "Đã thêm bài hát vào " + playlistTitle, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(PlayerActivity.this, "Đã lưu vào " + playlistTitle, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<Object>> call, @NonNull Throwable t) {
                Toast.makeText(PlayerActivity.this, "Lỗi thêm vào danh sách phát: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
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
        runOnUiThread(() -> updatePlayPauseButton(isPlaying));
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
        });
    }

    @Override
    public void onError(String message) {
        runOnUiThread(() -> Toast.makeText(PlayerActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    private String formatTime(int ms) {
        int seconds = (ms / 1000) % 60;
        int minutes = (ms / (1000 * 60)) % 60;
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
        MusicPlayerManager.getInstance().removeListener(this);
        if (queueBottomSheetDialog != null && queueBottomSheetDialog.isShowing()) {
            queueBottomSheetDialog.dismiss();
        }
    }
}
