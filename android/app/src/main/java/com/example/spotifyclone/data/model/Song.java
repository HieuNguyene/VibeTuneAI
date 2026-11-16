package com.example.spotifyclone.data.model;

import com.google.gson.annotations.SerializedName;

public class Song {
    @SerializedName(value = "_id", alternate = {"id"})
    private String id;

    @SerializedName("title")
    private String title;

    @SerializedName("artistId")
    private Artist artistId;

    @SerializedName("artist")
    private Artist artist;

    @SerializedName("artistName")
    private String directArtistName;

    @SerializedName("albumId")
    private Album albumId;

    @SerializedName(value = "duration", alternate = {"durationSec"})
    private int duration;

    @SerializedName(value = "audioUrl", alternate = {"streamUrl"})
    private String audioUrl;

    @SerializedName(value = "coverImageUrl", alternate = {"coverUrl"})
    private String coverImageUrl;

    @SerializedName("lyrics")
    private String lyrics;

    @SerializedName("composer")
    private String composer;

    @SerializedName("playsCount")
    private long playsCount;

    @SerializedName("likesCount")
    private long likesCount;

    @SerializedName("isLiked")
    private boolean isLiked;

    public String getId() { return id; }
    public String getTitle() { return title; }
    public Artist getArtist() { return artist != null ? artist : artistId; }

    public String getArtistName() {
        if (directArtistName != null && !directArtistName.trim().isEmpty()) {
            return directArtistName.trim();
        }
        if (artist != null && artist.getName() != null && !artist.getName().trim().isEmpty()) {
            return artist.getName().trim();
        }
        if (artistId != null && artistId.getName() != null && !artistId.getName().trim().isEmpty()) {
            return artistId.getName().trim();
        }
        return "Nghệ sĩ";
    }

    public Album getAlbum() { return albumId; }

    public String getAlbumTitle() {
        if (albumId != null && albumId.getTitle() != null) {
            return albumId.getTitle();
        }
        return "";
    }

    public int getDuration() { return duration; }
    public String getAudioUrl() { return audioUrl; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public String getLyrics() { return lyrics; }
    public String getComposer() { return composer != null ? composer.trim() : ""; }
    public long getPlaysCount() { return playsCount; }
    public long getLikesCount() { return likesCount; }
    @SerializedName("similarityScore")
    private Double similarityScore;

    public Double getSimilarityScore() { return similarityScore; }
    public void setSimilarityScore(Double similarityScore) { this.similarityScore = similarityScore; }

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
