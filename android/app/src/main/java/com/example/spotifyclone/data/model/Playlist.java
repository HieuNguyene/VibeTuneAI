package com.example.spotifyclone.data.model;

import com.google.gson.annotations.SerializedName;

public class Playlist {
    @SerializedName("_id")
    private String id;

    @SerializedName("title")
    private String title;

    @SerializedName("description")
    private String description;

    @SerializedName("coverImageUrl")
    private String coverImageUrl;

    @SerializedName("isPublic")
    private boolean isPublic;

    @SerializedName("isAiGenerated")
    private boolean isAiGenerated;

    @SerializedName("aiPrompt")
    private String aiPrompt;

    @SerializedName("totalTracks")
    private int totalTracks;

    @SerializedName("tracks")
    private java.util.List<Song> tracks;

    @SerializedName("creatorId")
    private com.google.gson.JsonElement creatorId;

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public boolean isPublic() { return isPublic; }
    public boolean isAiGenerated() { return isAiGenerated; }
    public String getAiPrompt() { return aiPrompt; }
    public int getTotalTracks() { return totalTracks; }
    public java.util.List<Song> getTracks() { return tracks; }

    public String getCreatorName() {
        if (creatorId != null && creatorId.isJsonObject()) {
            com.google.gson.JsonObject obj = creatorId.getAsJsonObject();
            if (obj.has("displayName") && !obj.get("displayName").isJsonNull()) {
                return obj.get("displayName").getAsString();
            } else if (obj.has("username") && !obj.get("username").isJsonNull()) {
                return obj.get("username").getAsString();
            }
        }
        return null;
    }

    public String getCreatorIdString() {
        if (creatorId != null) {
            if (creatorId.isJsonObject()) {
                com.google.gson.JsonObject obj = creatorId.getAsJsonObject();
                if (obj.has("_id") && !obj.get("_id").isJsonNull()) {
                    return obj.get("_id").getAsString();
                }
            } else if (creatorId.isJsonPrimitive()) {
                return creatorId.getAsString();
            }
        }
        return null;
    }

    public void setId(String id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setCoverImageUrl(String coverImageUrl) { this.coverImageUrl = coverImageUrl; }
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }
    public void setTotalTracks(int totalTracks) { this.totalTracks = totalTracks; }
    public void setTracks(java.util.List<Song> tracks) { this.tracks = tracks; }

    @SerializedName("isFavorite")
    private boolean isFavorite;

    public boolean isFavorite() { return isFavorite; }
    public void setFavorite(boolean favorite) { this.isFavorite = favorite; }

    @SerializedName("isLocked")
    private boolean isLocked;

    public boolean isLocked() { return isLocked; }
    public void setLocked(boolean locked) { this.isLocked = locked; }
}
