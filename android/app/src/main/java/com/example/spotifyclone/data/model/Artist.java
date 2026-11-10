package com.example.spotifyclone.data.model;

import com.google.gson.annotations.SerializedName;

public class Artist {
    @SerializedName("_id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("slug")
    private String slug;

    @SerializedName("avatarUrl")
    private String avatarUrl;

    @SerializedName("bio")
    private String bio;

    @SerializedName("followersCount")
    private long followersCount;

    @SerializedName("isVerified")
    private boolean isVerified;

    public String getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getBio() { return bio; }
    public long getFollowersCount() { return followersCount; }
    public boolean isVerified() { return isVerified; }
}
