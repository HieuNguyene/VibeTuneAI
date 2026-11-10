package com.example.spotifyclone.data.model;

import com.google.gson.annotations.SerializedName;

public class Song {
    @SerializedName("_id")
    private String id;

    @SerializedName("title")
    private String title;

    @SerializedName("artistId")
    private Artist artistId;

    @SerializedName("duration")
    private int duration;

    @SerializedName("audioUrl")
    private String audioUrl;

    @SerializedName("coverImageUrl")
    private String coverImageUrl;

    @SerializedName("lyrics")
    private String lyrics;

    @SerializedName("playsCount")
    private long playsCount;

    @SerializedName("likesCount")
    private long likesCount;

    @SerializedName("isLiked")
    private boolean isLiked;

    public String getId() { return id; }
    public String getTitle() { return title; }
    public Artist getArtist() { return artistId; }

    public String getArtistName() {
        if (artistId != null && artistId.getName() != null) {
            return artistId.getName();
        }
        return "Nghệ sĩ";
    }

    public int getDuration() { return duration; }
    public String getAudioUrl() { return audioUrl; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public String getLyrics() { return lyrics; }
    public long getPlaysCount() { return playsCount; }
    public long getLikesCount() { return likesCount; }
    public boolean isLiked() { return isLiked; }
    public void setLiked(boolean liked) { isLiked = liked; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Song song = (Song) o;
        return id != null ? id.equals(song.id) : song.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
