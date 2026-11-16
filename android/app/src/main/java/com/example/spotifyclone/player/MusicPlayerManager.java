package com.example.spotifyclone.player;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.spotifyclone.SpotifyApp;
import com.example.spotifyclone.data.model.ApiResponse;
import com.example.spotifyclone.data.model.MoodFlowNextResponse;
import com.example.spotifyclone.data.model.MoodFlowSessionRequest;
import com.example.spotifyclone.data.model.MoodFlowSessionResponse;
import com.example.spotifyclone.data.model.Song;
import com.example.spotifyclone.data.remote.ApiClient;

import androidx.annotation.NonNull;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

public class MusicPlayerManager {
    private static final String TAG = "MusicPlayerManager";
    private static MusicPlayerManager instance;

    // Repeat Mode Constants
    public static final int REPEAT_OFF = 0;
    public static final int REPEAT_ALL = 1;
    public static final int REPEAT_ONE = 2;

    public interface PlayerListener {
        void onTrackChanged(String title, String artist, String coverUrl);
        void onPlaybackStateChanged(boolean isPlaying);
        void onProgressUpdate(int currentMs, int totalMs);
        void onError(String message);
        default void onSongChanged(Song song) {}
        default void onFavoriteStateChanged(String songId, boolean isLiked) {}
        default void onRepeatModeChanged(int repeatMode) {}
        default void onShuffleModeChanged(boolean isShuffle) {}
        default void onQueueChanged(List<Song> queue, int currentIndex) {}
        default void onMoodFlowChanged(boolean isActive, String arc, String reason) {}
        default void onPlaybackSpeedChanged(float speed) {}
    }

    public static final float DEFAULT_PLAYBACK_SPEED = 1.0f;
    public static final float[] SUPPORTED_PLAYBACK_SPEEDS = new float[]{0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
    private float playbackSpeed = DEFAULT_PLAYBACK_SPEED;
    private boolean sonicSpeedEngaged = false;

    private MediaPlayer mediaPlayer;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<PlayerListener> listeners = new CopyOnWriteArrayList<>();

    private Song currentSong = null;
    private String currentSongId = "";
    private String currentTitle = "";
    private String currentArtist = "";
    private String currentCoverUrl = "";
    private String currentAudioUrl = "";
    private boolean isCurrentLiked = false;

    // Mood Flow State
    private boolean isMoodFlowMode = false;
    private String currentSessionId = "";
    private String currentArc = "steady";
    private String currentReason = "";
    private Song preloadedTrack = null;
    private String preloadedReason = "";
    private boolean isPreloading = false;

    // Audio Pipeline State Latch (prevents buffer underruns & crackles)
    private boolean isPrepared = false;
    private boolean isPreparing = false;

    // Playback Queue & Controls
    private final List<Song> currentQueue = new ArrayList<>();
    private final List<Song> originalQueue = new ArrayList<>();
    private int currentIndex = -1;
    private boolean isShuffle = false;
    private int repeatMode = REPEAT_OFF;
    private final Random random = new Random();

    // Sleep Timer Engine
    private final Handler sleepTimerHandler = new Handler(Looper.getMainLooper());
    private Runnable sleepTimerRunnable = null;
    private long sleepTimerEndTimeMs = 0;
    private boolean sleepTimerAtEndOfSong = false;

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && isPrepared && mediaPlayer.isPlaying()) {
                int current = mediaPlayer.getCurrentPosition();
                int total = mediaPlayer.getDuration();
                for (PlayerListener listener : listeners) {
                    listener.onProgressUpdate(current, total);
                }

                // Mood Flow Smart Preload: Tải trước bài tiếp theo khi bài hiện tại còn khoảng 20s
                if (isMoodFlowMode && preloadedTrack == null && !isPreloading && total > 0) {
                    int remainingMs = total - current;
                    if (remainingMs <= 20000 && remainingMs > 3000) {
                        fetchNextTrackPreload();
                    }
                }

                progressHandler.postDelayed(this, 500);
            }
        }
    };

    // Audio Focus & Noisy Receiver Management
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private boolean resumeOnFocusGain = false;
    private boolean isNoisyReceiverRegistered = false;

    private final BroadcastReceiver becomingNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                Log.d(TAG, "Headphones disconnected (ACTION_AUDIO_BECOMING_NOISY). Pausing playback.");
                if (isPlaying()) {
                    togglePlayPause();
                }
            }
        }
    };

    private final AudioManager.OnAudioFocusChangeListener focusChangeListener = focusChange -> {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS:
                Log.d(TAG, "Audio focus lost permanently");
                resumeOnFocusGain = false;
                if (isPlaying()) {
                    togglePlayPause();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                Log.d(TAG, "Audio focus lost temporarily (incoming call/alarm)");
                if (isPlaying()) {
                    resumeOnFocusGain = true;
                    togglePlayPause();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                Log.d(TAG, "Audio focus lost transient can duck: reducing volume");
                if (mediaPlayer != null && isPrepared && mediaPlayer.isPlaying()) {
                    try {
                        mediaPlayer.setVolume(0.2f, 0.2f);
                    } catch (Exception ignored) {}
                }
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                Log.d(TAG, "Audio focus regained: restoring volume and resuming if needed");
                if (mediaPlayer != null && isPrepared) {
                    try {
                        mediaPlayer.setVolume(1.0f, 1.0f);
                    } catch (Exception ignored) {}
                    if (resumeOnFocusGain && !mediaPlayer.isPlaying()) {
                        resumeOnFocusGain = false;
                        togglePlayPause();
                    }
                }
                break;
        }
    };

    private boolean requestAudioFocus() {
        if (audioManager == null) {
            Context ctx = SpotifyApp.getAppContext();
            if (ctx != null) {
                audioManager = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            }
        }
        if (audioManager == null) return true;

        if (audioFocusRequest == null) {
            AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(focusChangeListener, new Handler(Looper.getMainLooper()))
                    .build();
        }
        int res = audioManager.requestAudioFocus(audioFocusRequest);
        return res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void abandonAudioFocus() {
        if (audioManager != null && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        }
    }

    private void registerBecomingNoisyReceiver() {
        if (!isNoisyReceiverRegistered) {
            Context ctx = SpotifyApp.getAppContext();
            if (ctx != null) {
                try {
                    ctx.registerReceiver(
                            becomingNoisyReceiver,
                            new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                    );
                    isNoisyReceiverRegistered = true;
                } catch (Exception ignored) {}
            }
        }
    }

    private void unregisterBecomingNoisyReceiver() {
        if (isNoisyReceiverRegistered) {
            Context ctx = SpotifyApp.getAppContext();
            if (ctx != null) {
                try {
                    ctx.unregisterReceiver(becomingNoisyReceiver);
                } catch (Exception ignored) {}
                isNoisyReceiverRegistered = false;
            }
        }
    }

    private MusicPlayerManager() {
        initMediaPlayer();
    }

    public static synchronized MusicPlayerManager getInstance() {
        if (instance == null) {
            instance = new MusicPlayerManager();
        }
        return instance;
    }

    private void initMediaPlayer() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.release();
            } catch (Exception ignored) {}
        }
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioAttributes(
                new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
        );

        sonicSpeedEngaged = false;
        mediaPlayer.setOnPreparedListener(mp -> {
            isPreparing = false;
            isPrepared = true;
            if (requestAudioFocus()) {
                if (Math.abs(playbackSpeed - DEFAULT_PLAYBACK_SPEED) >= 0.01f) {
                    // Custom speed: setPlaybackParams cleanly sets rate and starts playback without double-start race
                    applyPlaybackSpeedToPlayer(mp);
                } else {
                    // Normal 1.0x playback: start native hardware decoder directly (no Sonic buffer drift)
                    if (!mp.isPlaying()) {
                        mp.start();
                    }
                }
                notifyPlaybackState(true);
                startProgressUpdates();
                Log.d(TAG, "Audio started playing cleanly (speed=" + playbackSpeed + "x): " + currentAudioUrl);
            } else {
                Log.d(TAG, "Audio focus not granted yet");
            }
        });

        mediaPlayer.setOnCompletionListener(mp -> {
            notifyPlaybackState(false);
            stopProgressUpdates();
            Log.d(TAG, "Audio playback completed. repeatMode=" + repeatMode);

            if (sleepTimerAtEndOfSong) {
                sleepTimerAtEndOfSong = false;
                Log.d(TAG, "Sleep timer at end of song fired, pausing playback.");
                return;
            }

            if (isMoodFlowMode) {
                logListeningEvent("complete", getDuration() / 1000);
                playNextMoodFlow();
                return;
            }

            if (repeatMode == REPEAT_ONE) {
                // Loop same track
                seekTo(0);
                if (!mp.isPlaying()) {
                    mp.start();
                }
                notifyPlaybackState(true);
                startProgressUpdates();
            } else if (repeatMode == REPEAT_ALL) {
                // Loop to next track or wrap to start
                playNext();
            } else {
                // REPEAT_OFF: Play next only if available, or trigger smart autoplay
                if (currentIndex + 1 < currentQueue.size()) {
                    playNext();
                } else {
                    Log.d(TAG, "Reached end of queue with REPEAT_OFF, triggering smart autoplay");
                    triggerSmartAutoplay();
                }
            }
        });

        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            Log.e(TAG, "MediaPlayer error: what=" + what + ", extra=" + extra);
            isPreparing = false;
            isPrepared = false;
            notifyError("Không thể phát bài hát này");
            return false;
        });
    }

    public void playSong(Song song) {
        if (song == null) return;
        this.currentSong = song;
        this.currentSongId = song.getId() != null ? song.getId() : "";
        this.isCurrentLiked = song.isLiked();

        if (currentQueue.isEmpty() || !currentQueue.contains(song)) {
            currentQueue.clear();
            currentQueue.add(song);
            originalQueue.clear();
            originalQueue.add(song);
            currentIndex = 0;
        } else {
            currentIndex = currentQueue.indexOf(song);
        }

        notifyQueueChanged();
        playAudio(
                song.getTitle(),
                song.getArtistName(),
                ApiClient.resolveMediaUrl(song.getCoverImageUrl()),
                ApiClient.resolveMediaUrl(song.getAudioUrl())
        );
    }

    public void playSong(String title, String artist, String coverUrl, String audioUrl) {
        playAudio(title, artist, coverUrl, audioUrl);
    }

    public void playQueue(List<Song> songs, int startIndex) {
        if (songs == null || songs.isEmpty()) return;

        originalQueue.clear();
        originalQueue.addAll(songs);

        currentQueue.clear();
        currentQueue.addAll(songs);

        if (startIndex < 0 || startIndex >= currentQueue.size()) {
            startIndex = 0;
        }
        currentIndex = startIndex;
        Song selected = currentQueue.get(currentIndex);
        this.currentSong = selected;
        this.currentSongId = selected.getId() != null ? selected.getId() : "";
        this.isCurrentLiked = selected.isLiked();

        notifyQueueChanged();
        playAudio(
                selected.getTitle(),
                selected.getArtistName(),
                ApiClient.resolveMediaUrl(selected.getCoverImageUrl()),
                ApiClient.resolveMediaUrl(selected.getAudioUrl())
        );
    }

    public void playTrackAtIndex(int index) {
        if (currentQueue.isEmpty() || index < 0 || index >= currentQueue.size()) return;

        currentIndex = index;
        Song targetSong = currentQueue.get(currentIndex);
        this.currentSong = targetSong;
        this.currentSongId = targetSong.getId() != null ? targetSong.getId() : "";
        this.isCurrentLiked = targetSong.isLiked();

        notifyQueueChanged();
        playAudio(
                targetSong.getTitle(),
                targetSong.getArtistName(),
                ApiClient.resolveMediaUrl(targetSong.getCoverImageUrl()),
                ApiClient.resolveMediaUrl(targetSong.getAudioUrl())
        );
    }

    public void playNext() {
        if (isMoodFlowMode) {
            logListeningEvent("skip", getCurrentPosition() / 1000);
            playNextMoodFlow();
            return;
        }

        if (currentQueue.isEmpty()) return;

        if (isShuffle && currentQueue.size() > 1) {
            int nextIndex;
            do {
                nextIndex = random.nextInt(currentQueue.size());
            } while (nextIndex == currentIndex && currentQueue.size() > 1);
            currentIndex = nextIndex;
        } else {
            currentIndex = (currentIndex + 1) % currentQueue.size();
        }

        Song nextSong = currentQueue.get(currentIndex);
        this.currentSong = nextSong;
        this.currentSongId = nextSong.getId() != null ? nextSong.getId() : "";
        this.isCurrentLiked = nextSong.isLiked();

        notifyQueueChanged();
        playAudio(
                nextSong.getTitle(),
                nextSong.getArtistName(),
                ApiClient.resolveMediaUrl(nextSong.getCoverImageUrl()),
                ApiClient.resolveMediaUrl(nextSong.getAudioUrl())
        );
    }

    public void playPrevious() {
        if (currentQueue.isEmpty()) return;

        if (getCurrentPosition() > 3000) {
            seekTo(0);
            return;
        }

        if (isShuffle && currentQueue.size() > 1) {
            int prevIndex;
            do {
                prevIndex = random.nextInt(currentQueue.size());
            } while (prevIndex == currentIndex && currentQueue.size() > 1);
            currentIndex = prevIndex;
        } else {
            currentIndex = (currentIndex - 1 + currentQueue.size()) % currentQueue.size();
        }

        Song prevSong = currentQueue.get(currentIndex);
        this.currentSong = prevSong;
        this.currentSongId = prevSong.getId() != null ? prevSong.getId() : "";
        this.isCurrentLiked = prevSong.isLiked();

        notifyQueueChanged();
        playAudio(
                prevSong.getTitle(),
                prevSong.getArtistName(),
                ApiClient.resolveMediaUrl(prevSong.getCoverImageUrl()),
                ApiClient.resolveMediaUrl(prevSong.getAudioUrl())
        );
    }

    private void playAudio(String title, String artist, String coverUrl, String audioUrl) {
        if (audioUrl == null || audioUrl.isEmpty()) {
            notifyError("Đường dẫn bài hát không hợp lệ");
            return;
        }

        // LATCH CHECK: If same audio URL is already preparing or playing, avoid resetting AudioTrack
        if (audioUrl.equals(currentAudioUrl)) {
            if (isPreparing) {
                Log.d(TAG, "Audio already preparing, avoiding double-reset: " + audioUrl);
                return;
            }
            if (isPrepared) {
                if (!mediaPlayer.isPlaying()) {
                    mediaPlayer.start();
                    notifyPlaybackState(true);
                    startProgressUpdates();
                    com.example.spotifyclone.service.MusicPlaybackService.start(com.example.spotifyclone.SpotifyApp.getAppContext());
                }
                return;
            }
        }

        this.currentTitle = title != null ? title : "Unknown Title";
        this.currentArtist = artist != null ? artist : "Unknown Artist";
        this.currentCoverUrl = coverUrl != null ? coverUrl : "";
        this.currentAudioUrl = audioUrl;

        // Reset speed to normal 1.0x when playing a new track (synchronized with Web Admin)
        this.playbackSpeed = DEFAULT_PLAYBACK_SPEED;
        this.sonicSpeedEngaged = false;
        notifyPlaybackSpeedChanged(DEFAULT_PLAYBACK_SPEED);

        notifyTrackChanged();
        recordHistory(currentSong);
        com.example.spotifyclone.service.MusicPlaybackService.start(com.example.spotifyclone.SpotifyApp.getAppContext());

        try {
            stopProgressUpdates();
            isPrepared = false;
            isPreparing = true;

            mediaPlayer.reset();
            mediaPlayer.setDataSource(audioUrl);
            mediaPlayer.prepareAsync();
            Log.d(TAG, "Preparing audio smoothly: " + audioUrl);
        } catch (IOException e) {
            Log.e(TAG, "Failed to setDataSource", e);
            isPreparing = false;
            isPrepared = false;
            notifyError("Lỗi kết nối âm thanh: " + e.getMessage());
        }
    }

    public void togglePlayPause() {
        if (mediaPlayer == null || !isPrepared) return;

        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            notifyPlaybackState(false);
            stopProgressUpdates();
        } else {
            requestAudioFocus();
            if (Math.abs(playbackSpeed - DEFAULT_PLAYBACK_SPEED) >= 0.01f) {
                applyPlaybackSpeedToPlayer(mediaPlayer);
            } else {
                if (!mediaPlayer.isPlaying()) {
                    mediaPlayer.start();
                }
            }
            notifyPlaybackState(true);
            startProgressUpdates();
            com.example.spotifyclone.service.MusicPlaybackService.start(com.example.spotifyclone.SpotifyApp.getAppContext());
        }
    }

    public void stop() {
        if (mediaPlayer != null) {
            stopProgressUpdates();
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.reset();
            isPrepared = false;
            isPreparing = false;
            sonicSpeedEngaged = false;
            playbackSpeed = DEFAULT_PLAYBACK_SPEED;
            currentSong = null;
            currentSongId = "";
            currentTitle = "";
            currentArtist = "";
            currentCoverUrl = "";
            currentAudioUrl = "";
            isCurrentLiked = false;
            abandonAudioFocus();
            notifyPlaybackState(false);
            notifyTrackChanged();
            com.example.spotifyclone.service.MusicPlaybackService.stop(com.example.spotifyclone.SpotifyApp.getAppContext());
        }
    }

    public void seekTo(int positionMs) {
        if (mediaPlayer != null && isPrepared) {
            int duration = Math.max(0, mediaPlayer.getDuration());
            int clamped = duration > 0 ? Math.max(0, Math.min(duration, positionMs)) : Math.max(0, positionMs);
            mediaPlayer.seekTo(clamped);
            for (PlayerListener l : listeners) {
                l.onProgressUpdate(clamped, duration);
            }
        }
    }

    public void seekBy(int deltaMs) {
        if (mediaPlayer != null && isPrepared) {
            seekTo(mediaPlayer.getCurrentPosition() + deltaMs);
        }
    }

    public float getPlaybackSpeed() {
        return playbackSpeed;
    }

    public static float normalizeSpeedValue(float speed) {
        if (Float.isNaN(speed) || Float.isInfinite(speed) || speed <= 0f) {
            return DEFAULT_PLAYBACK_SPEED;
        }
        float closest = DEFAULT_PLAYBACK_SPEED;
        float minDiff = Float.MAX_VALUE;
        for (float opt : SUPPORTED_PLAYBACK_SPEEDS) {
            float diff = Math.abs(opt - speed);
            if (diff < minDiff) {
                minDiff = diff;
                closest = opt;
            }
        }
        return Math.abs(closest - DEFAULT_PLAYBACK_SPEED) < 0.01f ? DEFAULT_PLAYBACK_SPEED : closest;
    }

    public void setPlaybackSpeed(float speed) {
        float normalized = normalizeSpeedValue(speed);
        this.playbackSpeed = normalized;
        if (mediaPlayer != null && isPrepared) {
            applyPlaybackSpeedToPlayer(mediaPlayer);
        }
        notifyPlaybackSpeedChanged(normalized);
    }

    private void notifyPlaybackSpeedChanged(float speed) {
        for (PlayerListener l : listeners) {
            try {
                l.onPlaybackSpeedChanged(speed);
            } catch (Exception ignored) {}
        }
    }

    public void resetPlaybackSpeed() {
        setPlaybackSpeed(DEFAULT_PLAYBACK_SPEED);
    }

    private void applyPlaybackSpeedToPlayer(MediaPlayer mp) {
        if (mp == null || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) {
            return;
        }
        boolean isCustom = Math.abs(playbackSpeed - DEFAULT_PLAYBACK_SPEED) >= 0.01f;
        // If neither custom nor previously engaged, native playback is already 1.0x
        if (!isCustom && !sonicSpeedEngaged) {
            return;
        }
        try {
            android.media.PlaybackParams params = new android.media.PlaybackParams();
            params.allowDefaults();
            params.setSpeed(playbackSpeed);
            params.setPitch(1.0f);
            params.setAudioFallbackMode(android.media.PlaybackParams.AUDIO_FALLBACK_MODE_DEFAULT);
            mp.setPlaybackParams(params);
            sonicSpeedEngaged = isCustom;
            Log.d(TAG, "Applied PlaybackParams cleanly (speed=" + playbackSpeed + "x, isCustom=" + isCustom + ")");
        } catch (Exception e) {
            Log.w(TAG, "Failed to apply PlaybackParams: " + e.getMessage());
        }
    }

    public boolean isPlaying() {
        return mediaPlayer != null && isPrepared && mediaPlayer.isPlaying();
    }

    public boolean isPreparing() {
        return isPreparing;
    }

    public boolean isPrepared() {
        return isPrepared;
    }

    public int getCurrentPosition() {
        if (mediaPlayer != null && isPrepared) {
            return mediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    public int getDuration() {
        if (mediaPlayer != null && isPrepared) {
            return mediaPlayer.getDuration();
        }
        return 0;
    }

    public Song getCurrentSong() { return currentSong; }
    public String getCurrentSongId() { return currentSongId; }
    public String getCurrentTitle() { return currentTitle; }
    public String getCurrentArtist() { return currentArtist; }
    public String getCurrentCoverUrl() { return currentCoverUrl; }
    public String getCurrentAudioUrl() { return currentAudioUrl; }
    public boolean isCurrentLiked() { return isCurrentLiked; }
    public List<Song> getCurrentQueue() { return new ArrayList<>(currentQueue); }
    public int getCurrentIndex() { return currentIndex; }
    public boolean isShuffle() { return isShuffle; }
    public int getRepeatMode() { return repeatMode; }

    public void setShuffle(boolean shuffle) {
        this.isShuffle = shuffle;
        notifyShuffleMode(shuffle);
    }

    public void setRepeatMode(int mode) {
        this.repeatMode = mode;
        notifyRepeatMode(mode);
    }

    public void setCurrentSongLiked(boolean liked) {
        this.isCurrentLiked = liked;
        if (currentSong != null) {
            currentSong.setLiked(liked);
        }
        notifyFavoriteState(currentSongId, liked);
    }

    public void addToQueue(Song song) {
        if (song == null) return;
        currentQueue.add(song);
        originalQueue.add(song);
        notifyQueueChanged();
    }

    public void hideCurrentSongFromQueue() {
        if (currentQueue.isEmpty()) return;
        if (currentQueue.size() > 1 && currentIndex >= 0 && currentIndex < currentQueue.size()) {
            currentQueue.remove(currentIndex);
            if (currentIndex >= currentQueue.size()) {
                currentIndex = 0;
            }
            Song nextSong = currentQueue.get(currentIndex);
            this.currentSong = nextSong;
            this.currentSongId = nextSong.getId() != null ? nextSong.getId() : "";
            this.isCurrentLiked = nextSong.isLiked();
            notifyQueueChanged();
            playAudio(
                    nextSong.getTitle(),
                    nextSong.getArtistName(),
                    ApiClient.resolveMediaUrl(nextSong.getCoverImageUrl()),
                    ApiClient.resolveMediaUrl(nextSong.getAudioUrl())
            );
        } else {
            stop();
        }
    }

    public void startSleepTimer(int minutes) {
        cancelSleepTimer();
        if (minutes <= 0) return;
        sleepTimerEndTimeMs = System.currentTimeMillis() + (minutes * 60 * 1000L);
        sleepTimerRunnable = () -> {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                notifyPlaybackState(false);
                stopProgressUpdates();
            }
            cancelSleepTimer();
            Log.d(TAG, "Sleep timer expired, playback paused.");
        };
        sleepTimerHandler.postDelayed(sleepTimerRunnable, minutes * 60 * 1000L);
    }

    public void setSleepTimerAtEndOfSong(boolean enable) {
        cancelSleepTimer();
        this.sleepTimerAtEndOfSong = enable;
    }

    public void cancelSleepTimer() {
        if (sleepTimerRunnable != null) {
            sleepTimerHandler.removeCallbacks(sleepTimerRunnable);
            sleepTimerRunnable = null;
        }
        sleepTimerEndTimeMs = 0;
        sleepTimerAtEndOfSong = false;
    }

    public boolean isSleepTimerActive() {
        return (sleepTimerRunnable != null && System.currentTimeMillis() < sleepTimerEndTimeMs) || sleepTimerAtEndOfSong;
    }

    public String getSleepTimerDescription() {
        if (sleepTimerAtEndOfSong) {
            return "Khi kết thúc bài hát";
        }
        if (sleepTimerRunnable != null && sleepTimerEndTimeMs > System.currentTimeMillis()) {
            long remainingMinutes = Math.max(1, (sleepTimerEndTimeMs - System.currentTimeMillis() + 59999) / 60000L);
            return remainingMinutes + " phút còn lại";
        }
        return "Chưa bật";
    }

    public void addListener(PlayerListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
            if (!currentTitle.isEmpty()) {
                listener.onTrackChanged(currentTitle, currentArtist, currentCoverUrl);
                if (currentSong != null) {
                    listener.onSongChanged(currentSong);
                }
                listener.onFavoriteStateChanged(currentSongId, isCurrentLiked);
                listener.onPlaybackStateChanged(isPlaying());
                listener.onShuffleModeChanged(isShuffle);
                listener.onRepeatModeChanged(repeatMode);
                listener.onQueueChanged(currentQueue, currentIndex);
                if (isPrepared) {
                    listener.onProgressUpdate(getCurrentPosition(), getDuration());
                }
            }
            listener.onMoodFlowChanged(isMoodFlowMode, currentArc, currentReason);
        }
    }

    public void removeListener(PlayerListener listener) {
        listeners.remove(listener);
    }

    private void notifyTrackChanged() {
        for (PlayerListener listener : listeners) {
            listener.onTrackChanged(currentTitle, currentArtist, currentCoverUrl);
            if (currentSong != null) {
                listener.onSongChanged(currentSong);
            }
            listener.onFavoriteStateChanged(currentSongId, isCurrentLiked);
        }
    }

    private void notifyPlaybackState(boolean isPlaying) {
        if (isPlaying) {
            registerBecomingNoisyReceiver();
        } else {
            unregisterBecomingNoisyReceiver();
        }
        for (PlayerListener listener : listeners) {
            listener.onPlaybackStateChanged(isPlaying);
        }
    }

    private void notifyFavoriteState(String songId, boolean liked) {
        for (PlayerListener listener : listeners) {
            listener.onFavoriteStateChanged(songId, liked);
        }
    }

    private void notifyRepeatMode(int mode) {
        for (PlayerListener listener : listeners) {
            listener.onRepeatModeChanged(mode);
        }
    }

    private void notifyShuffleMode(boolean shuffle) {
        for (PlayerListener listener : listeners) {
            listener.onShuffleModeChanged(shuffle);
        }
    }

    private void notifyQueueChanged() {
        for (PlayerListener listener : listeners) {
            listener.onQueueChanged(currentQueue, currentIndex);
        }
    }

    private void notifyError(String msg) {
        for (PlayerListener listener : listeners) {
            listener.onError(msg);
        }
    }

    private void startProgressUpdates() {
        stopProgressUpdates();
        progressHandler.post(progressRunnable);
    }

    private void stopProgressUpdates() {
        progressHandler.removeCallbacks(progressRunnable);
    }

    private void recordHistory(Song song) {
        if (song == null || song.getId() == null || song.getId().isEmpty()) return;
        try {
            android.content.Context ctx = com.example.spotifyclone.SpotifyApp.getAppContext();
            if (ctx != null) {
                com.example.spotifyclone.data.local.SessionManager.getInstance(ctx).saveRecentlyPlayedSong(song);
            }

            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("songId", song.getId());
            body.put("listenDuration", 10);
            body.put("playbackSource", "home_feed");

            ApiClient.getApiService().logPlayback(body).enqueue(new retrofit2.Callback<com.example.spotifyclone.data.model.ApiResponse<Object>>() {
                @Override
                public void onResponse(@androidx.annotation.NonNull retrofit2.Call<com.example.spotifyclone.data.model.ApiResponse<Object>> call,
                                       @androidx.annotation.NonNull retrofit2.Response<com.example.spotifyclone.data.model.ApiResponse<Object>> response) {}

                @Override
                public void onFailure(@androidx.annotation.NonNull retrofit2.Call<com.example.spotifyclone.data.model.ApiResponse<Object>> call,
                                      @androidx.annotation.NonNull Throwable t) {}
            });
        } catch (Exception e) {
            Log.e(TAG, "Error recording history", e);
        }
    }

    private void triggerSmartAutoplay() {
        if (currentSongId == null || currentSongId.isEmpty()) {
            Log.d(TAG, "No current song ID to fetch autoplay recommendations");
            return;
        }
        Log.d(TAG, "Triggering Smart Autoplay for songId: " + currentSongId);
        ApiClient.getApiService().getSimilarSongs(currentSongId).enqueue(new Callback<ApiResponse<List<Song>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<Song>>> call, @NonNull Response<ApiResponse<List<Song>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    List<Song> similar = response.body().getData();
                    if (!similar.isEmpty()) {
                        List<Song> candidates = new ArrayList<>();
                        for (Song s : similar) {
                            boolean exists = false;
                            for (Song q : currentQueue) {
                                if (q.getId() != null && q.getId().equals(s.getId())) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists) {
                                candidates.add(s);
                            }
                        }
                        if (candidates.isEmpty()) {
                            candidates.addAll(similar);
                        }
                        currentQueue.addAll(candidates);
                        originalQueue.addAll(candidates);
                        notifyQueueChanged();
                        playNext();
                        Log.d(TAG, "Smart Autoplay appended " + candidates.size() + " songs and continued playback.");
                    } else {
                        Log.d(TAG, "Smart Autoplay returned empty recommendation list");
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<Song>>> call, @NonNull Throwable t) {
                Log.e(TAG, "Smart Autoplay request failed", t);
            }
        });
    }

    // =========================================================================
    // MOOD FLOW ENGINE (AI NEXT TRACK RECOMMENDATION)
    // =========================================================================

    public boolean isMoodFlowMode() { return isMoodFlowMode; }
    public String getCurrentSessionId() { return currentSessionId; }
    public String getCurrentArc() { return currentArc; }
    public String getCurrentReason() { return currentReason; }

    public void startMoodFlow(String initialArc) {
        this.isMoodFlowMode = true;
        this.currentArc = (initialArc != null && !initialArc.isEmpty()) ? initialArc : "steady";
        this.preloadedTrack = null;
        this.preloadedReason = "";

        String trackIdToStart = currentSongId;
        Log.d(TAG, "Starting Mood Flow with trackId: " + trackIdToStart + ", arc: " + currentArc);

        MoodFlowSessionRequest req = new MoodFlowSessionRequest(trackIdToStart, currentArc);
        ApiClient.getApiService().createMoodFlowSession(req).enqueue(new Callback<ApiResponse<MoodFlowSessionResponse>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<MoodFlowSessionResponse>> call,
                                   @NonNull Response<ApiResponse<MoodFlowSessionResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    currentSessionId = response.body().getData().getSessionId();
                    Log.d(TAG, "Mood Flow session initialized successfully: " + currentSessionId);
                    notifyMoodFlowChanged();
                    fetchNextTrackPreload();
                } else {
                    Log.w(TAG, "Failed to start Mood Flow session, response code: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<MoodFlowSessionResponse>> call, @NonNull Throwable t) {
                Log.e(TAG, "Failed to start Mood Flow session", t);
            }
        });
    }

    public void stopMoodFlow() {
        this.isMoodFlowMode = false;
        this.currentSessionId = "";
        this.currentReason = "";
        this.preloadedTrack = null;
        this.preloadedReason = "";
        Log.d(TAG, "Mood Flow stopped, returning to standard playlist playback.");
        notifyMoodFlowChanged();
    }

    public void changeArc(String newArc) {
        if (newArc == null || newArc.isEmpty()) return;
        this.currentArc = newArc;
        this.preloadedTrack = null;
        this.preloadedReason = "";

        if (isMoodFlowMode && currentSessionId != null && !currentSessionId.isEmpty()) {
            Map<String, String> body = new HashMap<>();
            body.put("arc", newArc);
            ApiClient.getApiService().updateMoodFlowArc(currentSessionId, body).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                @Override
                public void onResponse(@NonNull Call<ApiResponse<Map<String, Object>>> call,
                                       @NonNull Response<ApiResponse<Map<String, Object>>> response) {
                    Log.d(TAG, "Mood Flow Arc updated to " + newArc);
                    fetchNextTrackPreload();
                    notifyMoodFlowChanged();
                }

                @Override
                public void onFailure(@NonNull Call<ApiResponse<Map<String, Object>>> call, @NonNull Throwable t) {
                    Log.e(TAG, "Failed to update Mood Flow arc", t);
                }
            });
        } else {
            notifyMoodFlowChanged();
        }
    }

    public void likeCurrentTrack() {
        if (currentSongId != null && !currentSongId.isEmpty()) {
            logListeningEvent("like", getCurrentPosition() / 1000);
            setCurrentSongLiked(true);
        }
    }

    public void dislikeCurrentTrack() {
        if (currentSongId != null && !currentSongId.isEmpty()) {
            logListeningEvent("dislike", getCurrentPosition() / 1000);
            setCurrentSongLiked(false);
            if (isMoodFlowMode) {
                playNext(); // Lập tức skip sang bài khác
            }
        }
    }

    public void logListeningEvent(String event, int positionSec) {
        if (currentSongId == null || currentSongId.isEmpty()) return;

        Map<String, Object> body = new HashMap<>();
        body.put("trackId", currentSongId);
        body.put("event", event);
        body.put("positionSec", Math.max(0, positionSec));
        body.put("source", isMoodFlowMode ? "moodflow" : "browse");
        if (isMoodFlowMode && currentSessionId != null && !currentSessionId.isEmpty()) {
            body.put("sessionId", currentSessionId);
        }

        ApiClient.getApiService().logEvent(body).enqueue(new Callback<ApiResponse<Object>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Object>> call, @NonNull Response<ApiResponse<Object>> response) {
                Log.d(TAG, "Logged event [" + event + "] at " + positionSec + "s for track " + currentSongId);
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<Object>> call, @NonNull Throwable t) {
                Log.w(TAG, "Failed to log event: " + event, t);
            }
        });
    }

    private void playNextMoodFlow() {
        if (preloadedTrack != null) {
            Song nextTrack = preloadedTrack;
            currentReason = preloadedReason != null ? preloadedReason : "";
            preloadedTrack = null;
            preloadedReason = "";
            playMoodFlowSong(nextTrack);
            fetchNextTrackPreload();
        } else {
            fetchNextTrackAndPlay();
        }
    }

    private void fetchNextTrackPreload() {
        if (!isMoodFlowMode || currentSessionId == null || currentSessionId.isEmpty() || isPreloading) return;
        isPreloading = true;

        ApiClient.getApiService().getMoodFlowNext(currentSessionId, false).enqueue(new Callback<ApiResponse<MoodFlowNextResponse>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<MoodFlowNextResponse>> call,
                                   @NonNull Response<ApiResponse<MoodFlowNextResponse>> response) {
                isPreloading = false;
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    MoodFlowNextResponse nextData = response.body().getData();
                    preloadedTrack = nextData.getTrack();
                    preloadedReason = nextData.getReason();
                    Log.d(TAG, "Preloaded Mood Flow track: " + (preloadedTrack != null ? preloadedTrack.getTitle() : "null"));
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<MoodFlowNextResponse>> call, @NonNull Throwable t) {
                isPreloading = false;
                Log.w(TAG, "Failed to preload next Mood Flow track", t);
            }
        });
    }

    private void fetchNextTrackAndPlay() {
        if (currentSessionId == null || currentSessionId.isEmpty()) {
            startMoodFlow(currentArc);
            return;
        }

        ApiClient.getApiService().getMoodFlowNext(currentSessionId, false).enqueue(new Callback<ApiResponse<MoodFlowNextResponse>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<MoodFlowNextResponse>> call,
                                   @NonNull Response<ApiResponse<MoodFlowNextResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    MoodFlowNextResponse nextData = response.body().getData();
                    Song song = nextData.getTrack();
                    currentReason = nextData.getReason() != null ? nextData.getReason() : "";
                    if (song != null) {
                        playMoodFlowSong(song);
                        fetchNextTrackPreload();
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<MoodFlowNextResponse>> call, @NonNull Throwable t) {
                Log.e(TAG, "Error fetching next Mood Flow track", t);
            }
        });
    }

    private void playMoodFlowSong(Song song) {
        if (song == null) return;
        this.currentSong = song;
        this.currentSongId = song.getId() != null ? song.getId() : "";
        this.isCurrentLiked = song.isLiked();

        boolean found = false;
        for (int i = 0; i < currentQueue.size(); i++) {
            if (currentQueue.get(i).getId() != null && currentQueue.get(i).getId().equals(song.getId())) {
                currentIndex = i;
                found = true;
                break;
            }
        }
        if (!found) {
            currentQueue.add(song);
            originalQueue.add(song);
            currentIndex = currentQueue.size() - 1;
        }

        notifyQueueChanged();
        notifyMoodFlowChanged();
        playAudio(
                song.getTitle(),
                song.getArtistName(),
                ApiClient.resolveMediaUrl(song.getCoverImageUrl()),
                ApiClient.resolveMediaUrl(song.getAudioUrl())
        );
    }

    private void notifyMoodFlowChanged() {
        for (PlayerListener listener : listeners) {
            listener.onMoodFlowChanged(isMoodFlowMode, currentArc, currentReason);
        }
    }
}
