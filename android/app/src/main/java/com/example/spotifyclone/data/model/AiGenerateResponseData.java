package com.example.spotifyclone.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class AiGenerateResponseData {
    @SerializedName("criteria")
    private AiCriteria criteria;

    @SerializedName("songs")
    private List<Song> songs;

    @SerializedName("playlist")
    private Playlist playlist;

    public AiCriteria getCriteria() { return criteria; }
    public List<Song> getSongs() { return songs; }
    public Playlist getPlaylist() { return playlist; }
}
