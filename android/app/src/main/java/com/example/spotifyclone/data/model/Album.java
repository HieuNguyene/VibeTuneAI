package com.example.spotifyclone.data.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Album implements Serializable {
    @SerializedName("_id")
    private String id;

    @SerializedName("title")
    private String title;

    @SerializedName("coverImageUrl")
    private String coverImageUrl;

    @SerializedName("releaseYear")
    private int releaseYear;

    @SerializedName("totalTracks")
    private int totalTracks;

    @SerializedName("artistId")
    private Artist artist;

    @SerializedName("tracks")
    private List<Song> tracks;

    public Album() {}

    public Album(String id, String title, String coverImageUrl, int releaseYear, int totalTracks) {
        this.id = id;
        this.title = title;
        this.coverImageUrl = coverImageUrl;
        this.releaseYear = releaseYear;
        this.totalTracks = totalTracks;
    }

    public String getId() { return id != null ? id : ""; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title != null ? title : ""; }
    public void setTitle(String title) { this.title = title; }

    public String getCoverImageUrl() { return coverImageUrl != null ? coverImageUrl : ""; }
    public void setCoverImageUrl(String coverImageUrl) { this.coverImageUrl = coverImageUrl; }

    public int getReleaseYear() { return releaseYear; }
    public void setReleaseYear(int releaseYear) { this.releaseYear = releaseYear; }

    public int getTotalTracks() { return totalTracks > 0 ? totalTracks : (tracks != null ? tracks.size() : 0); }
    public void setTotalTracks(int totalTracks) { this.totalTracks = totalTracks; }

    public Artist getArtist() { return artist; }
    public void setArtist(Artist artist) { this.artist = artist; }

    public String getArtistName() {
        return artist != null && artist.getName() != null ? artist.getName() : "Nhiều nghệ sĩ";
    }

    @SerializedName("isFavorite")
    private boolean isFavorite;

    public List<Song> getTracks() {
        return tracks != null ? tracks : new ArrayList<>();
    }

    public void setTracks(List<Song> tracks) {
        this.tracks = tracks;
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }
}
