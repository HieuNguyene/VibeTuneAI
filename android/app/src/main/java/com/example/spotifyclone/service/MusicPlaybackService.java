package com.example.spotifyclone.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.spotifyclone.R;
import com.example.spotifyclone.data.model.Song;
import com.example.spotifyclone.player.MusicPlayerManager;
import com.example.spotifyclone.presentation.player.PlayerActivity;

public class MusicPlaybackService extends Service implements MusicPlayerManager.PlayerListener {

    private static final String TAG = "MusicPlaybackService";

    public static final String ACTION_START = "com.example.spotifyclone.ACTION_START";
    public static final String ACTION_PLAY = "com.example.spotifyclone.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.example.spotifyclone.ACTION_PAUSE";
    public static final String ACTION_TOGGLE = "com.example.spotifyclone.ACTION_TOGGLE";
    public static final String ACTION_PREVIOUS = "com.example.spotifyclone.ACTION_PREVIOUS";
    public static final String ACTION_NEXT = "com.example.spotifyclone.ACTION_NEXT";
    public static final String ACTION_STOP = "com.example.spotifyclone.ACTION_STOP";

    public static final String CHANNEL_ID = "spotify_playback_channel";
    public static final int NOTIFICATION_ID = 1001;

    private MediaSessionCompat mediaSession;
    private NotificationManager notificationManager;
    private Bitmap currentCoverBitmap = null;
    private String lastCoverUrl = "";
    private boolean isForegroundRunning = false;

    public static void start(@NonNull Context context) {
        try {
            Intent intent = new Intent(context, MusicPlaybackService.class);
            intent.setAction(ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start MusicPlaybackService", e);
        }
    }

    public static void stop(@NonNull Context context) {
        try {
            Intent intent = new Intent(context, MusicPlaybackService.class);
            intent.setAction(ACTION_STOP);
            context.startService(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop MusicPlaybackService", e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
        initMediaSession();
        MusicPlayerManager.getInstance().addListener(this);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Spotify Music Playback",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Thanh thông báo điều khiển phát nhạc Spotify");
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void initMediaSession() {
        mediaSession = new MediaSessionCompat(this, "SpotifyCloneMediaSession");
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                MusicPlayerManager.getInstance().togglePlayPause();
            }

            @Override
            public void onPause() {
                MusicPlayerManager.getInstance().togglePlayPause();
            }

            @Override
            public void onSkipToNext() {
                MusicPlayerManager.getInstance().playNext();
            }

            @Override
            public void onSkipToPrevious() {
                MusicPlayerManager.getInstance().playPrevious();
            }

            @Override
            public void onStop() {
                MusicPlayerManager.getInstance().stop();
                stopForegroundService();
            }

            @Override
            public void onSeekTo(long pos) {
                MusicPlayerManager.getInstance().seekTo((int) pos);
            }
        });
        mediaSession.setActive(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            Log.d(TAG, "onStartCommand action: " + action);

            switch (action) {
                case ACTION_START:
                    updateNotificationAndForeground();
                    break;
                case ACTION_TOGGLE:
                    MusicPlayerManager.getInstance().togglePlayPause();
                    break;
                case ACTION_PLAY:
                    if (!MusicPlayerManager.getInstance().isPlaying()) {
                        MusicPlayerManager.getInstance().togglePlayPause();
                    }
                    break;
                case ACTION_PAUSE:
                    if (MusicPlayerManager.getInstance().isPlaying()) {
                        MusicPlayerManager.getInstance().togglePlayPause();
                    }
                    break;
                case ACTION_NEXT:
                    MusicPlayerManager.getInstance().playNext();
                    break;
                case ACTION_PREVIOUS:
                    MusicPlayerManager.getInstance().playPrevious();
                    break;
                case ACTION_STOP:
                    MusicPlayerManager.getInstance().stop();
                    stopForegroundService();
                    break;
            }
        }
        return START_STICKY;
    }

    private void updateNotificationAndForeground() {
        String title = MusicPlayerManager.getInstance().getCurrentTitle();
        String artist = MusicPlayerManager.getInstance().getCurrentArtist();
        String coverUrl = MusicPlayerManager.getInstance().getCurrentCoverUrl();

        if (title.isEmpty()) {
            title = "Spotify Clone";
        }
        if (artist.isEmpty()) {
            artist = "Sẵn sàng phát nhạc";
        }

        // Tải ảnh bìa album nếu có URL mới
        if (coverUrl != null && !coverUrl.isEmpty() && !coverUrl.equals(lastCoverUrl)) {
            lastCoverUrl = coverUrl;
            Glide.with(getApplicationContext())
                    .asBitmap()
                    .load(coverUrl)
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                            currentCoverBitmap = resource;
                            buildAndDispatchNotification();
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {}
                    });
        }

        buildAndDispatchNotification();
    }

    private void buildAndDispatchNotification() {
        String title = MusicPlayerManager.getInstance().getCurrentTitle();
        String artist = MusicPlayerManager.getInstance().getCurrentArtist();
        boolean isPlaying = MusicPlayerManager.getInstance().isPlaying();

        if (title.isEmpty()) {
            title = "Spotify Clone";
        }
        if (artist.isEmpty()) {
            artist = "Sẵn sàng phát nhạc";
        }

        // Intent khi nhấn vào thông báo -> Mở PlayerActivity
        Intent openIntent = new Intent(this, PlayerActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Các nút điều khiển
        PendingIntent prevIntent = createActionPendingIntent(ACTION_PREVIOUS, 1);
        PendingIntent toggleIntent = createActionPendingIntent(ACTION_TOGGLE, 2);
        PendingIntent nextIntent = createActionPendingIntent(ACTION_NEXT, 3);
        PendingIntent stopIntent = createActionPendingIntent(ACTION_STOP, 4);

        int playPauseIcon = isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow;
        String playPauseTitle = isPlaying ? "Tạm dừng" : "Phát";

        androidx.media.app.NotificationCompat.MediaStyle mediaStyle =
                new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2)
                        .setShowCancelButton(true)
                        .setCancelButtonIntent(stopIntent);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_play_arrow)
                .setContentTitle(title)
                .setContentText(artist)
                .setSubText("Spotify Clone")
                .setContentIntent(contentPendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setOngoing(isPlaying)
                .addAction(R.drawable.ic_skip_previous, "Bài trước", prevIntent)
                .addAction(playPauseIcon, playPauseTitle, toggleIntent)
                .addAction(R.drawable.ic_skip_next, "Bài sau", nextIntent)
                .setStyle(mediaStyle);

        if (currentCoverBitmap != null) {
            builder.setLargeIcon(currentCoverBitmap);
        }

        Notification notification = builder.build();

        // Cập nhật MediaSession State & Metadata
        updateMediaSession(title, artist, isPlaying);

        // Quản lý Foreground Service
        if (!isForegroundRunning) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceCompat.startForeground(
                            this,
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    );
                } else {
                    startForeground(NOTIFICATION_ID, notification);
                }
                isForegroundRunning = true;
            } catch (Exception e) {
                Log.e(TAG, "Error starting foreground service", e);
            }
        } else {
            if (notificationManager != null) {
                notificationManager.notify(NOTIFICATION_ID, notification);
            }
        }
    }

    private void updateMediaSession(String title, String artist, boolean isPlaying) {
        if (mediaSession == null) return;

        int state = isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        long position = MusicPlayerManager.getInstance().getCurrentPosition();

        PlaybackStateCompat playbackState = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                        PlaybackStateCompat.ACTION_PAUSE |
                        PlaybackStateCompat.ACTION_PLAY_PAUSE |
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                        PlaybackStateCompat.ACTION_STOP |
                        PlaybackStateCompat.ACTION_SEEK_TO
                )
                .setState(state, position, 1.0f)
                .build();
        mediaSession.setPlaybackState(playbackState);

        MediaMetadataCompat.Builder metaBuilder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, MusicPlayerManager.getInstance().getDuration());

        if (currentCoverBitmap != null) {
            metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, currentCoverBitmap);
        }

        mediaSession.setMetadata(metaBuilder.build());
    }

    private PendingIntent createActionPendingIntent(String action, int requestCode) {
        Intent intent = new Intent(this, MusicPlaybackService.class);
        intent.setAction(action);
        return PendingIntent.getService(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void stopForegroundService() {
        isForegroundRunning = false;
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
        stopSelf();
    }

    @Override
    public void onTrackChanged(String title, String artist, String coverUrl) {
        updateNotificationAndForeground();
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        updateNotificationAndForeground();
    }

    @Override
    public void onProgressUpdate(int currentMs, int totalMs) {}

    @Override
    public void onError(String message) {}

    @Override
    public void onSongChanged(Song song) {
        updateNotificationAndForeground();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.d(TAG, "Application task removed from Recents.");
        if (!MusicPlayerManager.getInstance().isPlaying()) {
            stopForegroundService();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        MusicPlayerManager.getInstance().removeListener(this);
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
        isForegroundRunning = false;
        Log.d(TAG, "MusicPlaybackService destroyed cleanly.");
    }
}
