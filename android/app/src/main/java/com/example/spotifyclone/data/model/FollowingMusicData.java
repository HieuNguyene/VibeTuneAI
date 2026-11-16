package com.example.spotifyclone.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

public class FollowingMusicData {
    @SerializedName("artists")
    private List<Artist> artists;

    @SerializedName("songs")
    private List<Song> songs;

    @SerializedName("totalArtists")
    private int totalArtists;

    @SerializedName("totalSongs")
    private int totalSongs;

    public List<Artist> getArtists() { return artists != null ? artists : new ArrayList<>(); }
    public List<Song> getSongs() { return songs != null ? songs : new ArrayList<>(); }
    public int getTotalArtists() { return totalArtists; }
    public int getTotalSongs() { return totalSongs; }
}
