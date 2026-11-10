package com.example.spotifyclone.data.model;

import com.google.gson.annotations.SerializedName;

public class CreatePlaylistRequest {
    @SerializedName("title")
    private String title;

    @SerializedName("description")
    private String description;

    @SerializedName("isPublic")
    private boolean isPublic;

    public CreatePlaylistRequest(String title, String description, boolean isPublic) {
        this.title = title;
        this.description = description;
        this.isPublic = isPublic;
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public boolean isPublic() { return isPublic; }
}
