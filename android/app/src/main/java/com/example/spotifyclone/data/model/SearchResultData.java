package com.example.spotifyclone.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

public class SearchResultData {
    @SerializedName("songs")
    private List<Song> songs;

    @SerializedName("artists")
    private List<Artist> artists;

    public List<Song> getSongs() {
        return songs != null ? songs : new ArrayList<>();
    }

    public void setSongs(List<Song> songs) {
        this.songs = songs;
    }

    public List<Artist> getArtists() {
        return artists != null ? artists : new ArrayList<>();
    }

    public void setArtists(List<Artist> artists) {
        this.artists = artists;
    }
}
