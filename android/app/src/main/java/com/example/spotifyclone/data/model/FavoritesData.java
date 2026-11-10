package com.example.spotifyclone.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class FavoritesData {
    @SerializedName("songs")
    private List<Song> songs;

    public List<Song> getSongs() {
        return songs;
    }

    public void setSongs(List<Song> songs) {
        this.songs = songs;
    }
}
