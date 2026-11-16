package com.example.spotifyclone.data.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class NotificationItem implements Serializable {
    @SerializedName(value = "id", alternate = {"_id"})
    private String id;

    @SerializedName("recipientId")
    private String recipientId;

    @SerializedName("senderId")
    private Object senderId; // Co the la String hoac User Object

    @SerializedName("senderName")
    private String senderName;

    @SerializedName("senderAvatar")
    private String senderAvatar;

    @SerializedName("title")
    private String title;

    @SerializedName("content")
    private String content;

    @SerializedName("imageUrl")
    private String imageUrl;

    @SerializedName("type")
    private String type; // ADMIN_ANNOUNCEMENT, PLAYLIST_LIKED, SYSTEM_UPDATE, GENERAL

    @SerializedName("metadata")
    private NotificationMetadata metadata;

    @SerializedName("isRead")
    private boolean isRead;

    @SerializedName("readAt")
    private String readAt;

    @SerializedName("createdAt")
    private String createdAt;

    @SerializedName("localDateKey")
    private String localDateKey;

    public NotificationItem() {}

    public static class NotificationMetadata implements Serializable {
        @SerializedName("playlistId")
        private String playlistId;

        @SerializedName("songId")
        private String songId;

        public String getPlaylistId() { return playlistId; }
        public void setPlaylistId(String playlistId) { this.playlistId = playlistId; }

        public String getSongId() { return songId; }
        public void setSongId(String songId) { this.songId = songId; }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }

    public String getSenderName() {
        if (senderName != null && !senderName.trim().isEmpty()) {
            return senderName;
        }
        return "Ban Quản trị VibeTune";
    }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getSenderAvatar() { return senderAvatar; }
    public void setSenderAvatar(String senderAvatar) { this.senderAvatar = senderAvatar; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getType() { return type != null ? type : "GENERAL"; }
    public void setType(String type) { this.type = type; }

    public NotificationMetadata getMetadata() { return metadata; }
    public void setMetadata(NotificationMetadata metadata) { this.metadata = metadata; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    public String getReadAt() { return readAt; }
    public void setReadAt(String readAt) { this.readAt = readAt; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getLocalDateKey() {
        if (localDateKey != null && !localDateKey.isEmpty()) {
            return localDateKey;
        }
        if (createdAt != null && !createdAt.isEmpty()) {
            try {
                SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                iso.setTimeZone(TimeZone.getTimeZone("UTC"));
                String clean = createdAt.split("\\.")[0];
                Date d = iso.parse(clean);
                if (d != null) {
                    SimpleDateFormat localFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    localFmt.setTimeZone(TimeZone.getDefault());
                    return localFmt.format(d);
                }
            } catch (Exception ignored) {}
        }
        return "Gần đây";
    }

    public void setLocalDateKey(String localDateKey) { this.localDateKey = localDateKey; }

    public String getFormattedRelativeTime() {
        if (createdAt == null || createdAt.isEmpty()) return "";
        try {
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            iso.setTimeZone(TimeZone.getTimeZone("UTC"));
            String clean = createdAt.split("\\.")[0];
            Date d = iso.parse(clean);
            if (d == null) return "";

            long diffMs = System.currentTimeMillis() - d.getTime();
            long diffMins = diffMs / 60000;
            long diffHours = diffMins / 60;
            long diffDays = diffHours / 24;

            if (diffMins < 1) return "Vừa xong";
            if (diffMins < 60) return diffMins + " phút trước";
            if (diffHours < 24) return diffHours + " giờ trước";
            if (diffDays == 1) return "Hôm qua";
            if (diffDays < 7) return diffDays + " ngày trước";

            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getDefault());
            return sdf.format(d);
        } catch (Exception e) {
            return "";
        }
    }

    public String getFullFormattedDateTime() {
        if (createdAt == null || createdAt.isEmpty()) return "";
        try {
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            iso.setTimeZone(TimeZone.getTimeZone("UTC"));
            String clean = createdAt.split("\\.")[0];
            Date d = iso.parse(clean);
            if (d == null) return createdAt;

            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm - dd/MM/yyyy", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getDefault());
            return sdf.format(d);
        } catch (Exception e) {
            return createdAt;
        }
    }
}
