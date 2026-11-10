package com.example.spotifyclone.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

public class HomeFeedData {
    @SerializedName("genres")
    private List<Genre> genres;

    @SerializedName("trending")
    private List<Song> trending;

    @SerializedName("newReleases")
    private List<Song> newReleases;

    @SerializedName("popularArtists")
    private List<Artist> popularArtists;

    @SerializedName("recentlyPlayed")
    private List<Song> recentlyPlayed;

    @SerializedName("popularAlbums")
    private List<Album> popularAlbums;

    @SerializedName("featuredPlaylists")
    private List<Playlist> featuredPlaylists;

    public List<Genre> getGenres() { return genres != null ? genres : new ArrayList<>(); }
    public List<Song> getTrending() { return trending != null ? trending : new ArrayList<>(); }
    public List<Song> getNewReleases() { return newReleases != null ? newReleases : new ArrayList<>(); }
    public List<Artist> getPopularArtists() { return popularArtists != null ? popularArtists : new ArrayList<>(); }
    public List<Song> getRecentlyPlayed() { return recentlyPlayed != null ? recentlyPlayed : new ArrayList<>(); }
    public List<Album> getPopularAlbums() { return popularAlbums != null ? popularAlbums : new ArrayList<>(); }
    public List<Playlist> getFeaturedPlaylists() { return featuredPlaylists != null ? featuredPlaylists : new ArrayList<>(); }
}
