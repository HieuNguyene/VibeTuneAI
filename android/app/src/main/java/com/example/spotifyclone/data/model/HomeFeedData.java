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

    @SerializedName("madeForYou")
    private List<Song> madeForYou;

    @SerializedName("popularArtists")
    private List<Artist> popularArtists;

    @SerializedName("recentlyPlayed")
    private List<Song> recentlyPlayed;

    @SerializedName("likedSongAlbums")
    private List<Album> likedSongAlbums;

    @SerializedName("recentPlaylists")
    private List<Playlist> recentPlaylists;

    @SerializedName("popularAlbums")
    private List<Album> popularAlbums;

    @SerializedName("featuredPlaylists")
    private List<Playlist> featuredPlaylists;

    @SerializedName("topMixes")
    private List<Song> topMixes;

    @SerializedName("popularRadios")
    private List<Playlist> popularRadios;

    @SerializedName("recommendedToday")
    private List<Song> recommendedToday;

    @SerializedName("listenAgain")
    private List<Song> listenAgain;

    @SerializedName("bestOfArtists")
    private List<Artist> bestOfArtists;

    public List<Genre> getGenres() { return genres != null ? genres : new ArrayList<>(); }
    public List<Song> getTrending() { return trending != null ? trending : new ArrayList<>(); }
    public List<Song> getNewReleases() { return newReleases != null ? newReleases : new ArrayList<>(); }
    public List<Song> getMadeForYou() { return madeForYou != null ? madeForYou : new ArrayList<>(); }
    public List<Artist> getPopularArtists() { return popularArtists != null ? popularArtists : new ArrayList<>(); }
    public List<Song> getRecentlyPlayed() { return recentlyPlayed != null ? recentlyPlayed : new ArrayList<>(); }
    public List<Album> getLikedSongAlbums() { return likedSongAlbums != null ? likedSongAlbums : new ArrayList<>(); }
    public List<Playlist> getRecentPlaylists() { return recentPlaylists != null ? recentPlaylists : new ArrayList<>(); }
    public List<Album> getPopularAlbums() { return popularAlbums != null ? popularAlbums : new ArrayList<>(); }
    public List<Playlist> getFeaturedPlaylists() { return featuredPlaylists != null ? featuredPlaylists : new ArrayList<>(); }
    public List<Song> getTopMixes() { return topMixes != null ? topMixes : getMadeForYou(); }
    public List<Playlist> getPopularRadios() { return popularRadios != null ? popularRadios : getFeaturedPlaylists(); }
    public List<Song> getRecommendedToday() { return recommendedToday != null ? recommendedToday : getNewReleases(); }
    public List<Song> getListenAgain() { return listenAgain != null ? listenAgain : getRecentlyPlayed(); }
    public List<Artist> getBestOfArtists() { return bestOfArtists != null ? bestOfArtists : getPopularArtists(); }
}
