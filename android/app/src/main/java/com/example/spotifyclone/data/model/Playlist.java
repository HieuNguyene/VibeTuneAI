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

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public boolean isPublic() { return isPublic; }
    public boolean isAiGenerated() { return isAiGenerated; }
    public String getAiPrompt() { return aiPrompt; }
    public int getTotalTracks() { return totalTracks; }
    public java.util.List<Song> getTracks() { return tracks; }

    public void setId(String id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setCoverImageUrl(String coverImageUrl) { this.coverImageUrl = coverImageUrl; }
    public void setTotalTracks(int totalTracks) { this.totalTracks = totalTracks; }
    public void setTracks(java.util.List<Song> tracks) { this.tracks = tracks; }
}
