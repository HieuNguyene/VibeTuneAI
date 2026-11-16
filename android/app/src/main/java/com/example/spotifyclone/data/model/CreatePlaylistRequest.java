package com.example.spotifyclone.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class CreatePlaylistRequest {
    @SerializedName("title")
    private String title;

    @SerializedName("description")
    private String description;

    @SerializedName("isPublic")
    private boolean isPublic;

    @SerializedName("coverImageBase64")
    private String coverImageBase64;

    @SerializedName("trackIds")
    private List<String> trackIds;

    public CreatePlaylistRequest(String title, String description, boolean isPublic) {
        this(title, description, isPublic, null, null);
    }

    public CreatePlaylistRequest(String title, String description, boolean isPublic, String coverImageBase64) {
        this(title, description, isPublic, coverImageBase64, null);
    }

    public CreatePlaylistRequest(String title, String description, boolean isPublic, String coverImageBase64, List<String> trackIds) {
        this.title = title;
        this.description = description;
        this.isPublic = isPublic;
        this.coverImageBase64 = coverImageBase64;
        this.trackIds = trackIds;
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public boolean isPublic() { return isPublic; }
    public String getCoverImageBase64() { return coverImageBase64; }
    public List<String> getTrackIds() { return trackIds; }

    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setPublic(boolean aPublic) { isPublic = aPublic; }
    public void setCoverImageBase64(String coverImageBase64) { this.coverImageBase64 = coverImageBase64; }
    public void setTrackIds(List<String> trackIds) { this.trackIds = trackIds; }
}
