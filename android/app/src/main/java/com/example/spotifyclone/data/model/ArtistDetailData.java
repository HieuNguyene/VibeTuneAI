package com.example.spotifyclone.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ArtistDetailData {
    @SerializedName("artist")
    private Artist artist;

    @SerializedName("songs")
    private List<Song> songs;

    @SerializedName("totalSongs")
    private int totalSongs;

    @SerializedName("albums")
    private List<Album> albums;

    public Artist getArtist() {
        return artist;
    }

    public void setArtist(Artist artist) {
        this.artist = artist;
    }

    public List<Song> getSongs() {
        return songs;
    }

    public void setSongs(List<Song> songs) {
        this.songs = songs;
    }

    public int getTotalSongs() {
        return totalSongs;
    }

    public void setTotalSongs(int totalSongs) {
        this.totalSongs = totalSongs;
    }

    public List<Album> getAlbums() {
        return albums;
    }

    public void setAlbums(List<Album> albums) {
        this.albums = albums;
    }
}
