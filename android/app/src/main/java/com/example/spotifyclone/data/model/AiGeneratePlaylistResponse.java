package com.example.spotifyclone.data.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.List;

public class AiGeneratePlaylistResponse implements Serializable {

    @SerializedName("playlist")
    private Playlist playlist;

    @SerializedName("songs")
    private List<Song> songs;

    @SerializedName("message")
    private String message;

    public AiGeneratePlaylistResponse() {
    }

    public Playlist getPlaylist() {
        return playlist;
    }

    public void setPlaylist(Playlist playlist) {
        this.playlist = playlist;
    }

    public List<Song> getSongs() {
        return songs;
    }

    public void setSongs(List<Song> songs) {
        this.songs = songs;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
