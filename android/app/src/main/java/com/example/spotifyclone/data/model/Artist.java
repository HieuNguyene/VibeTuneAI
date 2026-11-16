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

    @SerializedName("isFollowed")
    private boolean isFollowed;

    @SerializedName("songsCount")
    private int songsCount;

    public Artist() {}

    public Artist(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getBio() { return bio; }
    public long getFollowersCount() { return followersCount; }
    public void setFollowersCount(long followersCount) { this.followersCount = followersCount; }
    public boolean isVerified() { return isVerified; }
    public boolean isFollowed() { return isFollowed; }
    public void setFollowed(boolean followed) { isFollowed = followed; }
    public int getSongsCount() { return songsCount; }
    public void setSongsCount(int songsCount) { this.songsCount = songsCount; }
}
