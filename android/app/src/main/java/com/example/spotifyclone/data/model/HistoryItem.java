package com.example.spotifyclone.data.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class HistoryItem implements Serializable {
    @SerializedName("_id")
    private String id;

    @SerializedName("timestamp")
    private String timestamp;

    @SerializedName("date")
    private String date; // YYYY-MM-DD

    @SerializedName("listenDuration")
    private long listenDuration;

    @SerializedName("completed")
    private boolean completed;

    @SerializedName("playbackSource")
    private String playbackSource;

    @SerializedName("song")
    private Song song;

    public HistoryItem() {}

    public String getId() { return id != null ? id : ""; }
    public void setId(String id) { this.id = id; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getDate() { return date != null ? date : ""; }
    public void setDate(String date) { this.date = date; }

    public long getListenDuration() { return listenDuration; }
    public void setListenDuration(long listenDuration) { this.listenDuration = listenDuration; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public String getPlaybackSource() { return playbackSource; }
    public void setPlaybackSource(String playbackSource) { this.playbackSource = playbackSource; }

    public Song getSong() { return song; }
    public void setSong(Song song) { this.song = song; }

    /**
     * Parse timestamp chuẩn ISO-8601 từ server với múi giờ UTC
     */
    public Date getParsedDate() {
        if (timestamp == null || timestamp.trim().isEmpty()) {
            return new Date();
        }
        String cleanTs = timestamp.trim();
        try {
            SimpleDateFormat sdf;
            if (cleanTs.contains(".")) {
                // Ví dụ: 2026-11-13T17:47:03.806Z
                sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US);
            } else {
                // Ví dụ: 2026-11-13T17:47:03Z
                sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            }
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

            String parseTarget = cleanTs;
            if (parseTarget.endsWith("Z")) {
                parseTarget = parseTarget.substring(0, parseTarget.length() - 1);
            }
            Date d = sdf.parse(parseTarget);
            if (d != null) {
                return d;
            }
        } catch (Exception ignored) {}

        return new Date();
    }

    /**
     * Định dạng giờ nghe (HH:mm) theo đúng múi giờ thực tế trên máy thiết bị
     */
    public String getFormattedTime() {
        Date d = getParsedDate();
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        timeFormat.setTimeZone(TimeZone.getDefault());
        return timeFormat.format(d);
    }

    /**
     * Lấy date key (yyyy-MM-dd) theo múi giờ thực tế của máy
     */
    public String getLocalDateKey() {
        Date d = getParsedDate();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        df.setTimeZone(TimeZone.getDefault());
        return df.format(d);
    }
}
