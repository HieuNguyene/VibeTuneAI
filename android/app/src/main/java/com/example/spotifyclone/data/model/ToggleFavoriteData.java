package com.example.spotifyclone.data.model;

import com.google.gson.annotations.SerializedName;

public class ToggleFavoriteData {
    @SerializedName("isLiked")
    private boolean isLiked;

    @SerializedName("songId")
    private String songId;

    @SerializedName("likesCount")
    private long likesCount;

    public boolean isLiked() { return isLiked; }
    public String getSongId() { return songId; }
    public long getLikesCount() { return likesCount; }
}
