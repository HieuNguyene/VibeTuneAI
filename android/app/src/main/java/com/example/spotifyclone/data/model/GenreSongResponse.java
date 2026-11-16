package com.example.spotifyclone.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

public class GenreSongResponse {
    @SerializedName("genre")
    private Genre genre;

    @SerializedName("songs")
    private List<Song> songs;

    @SerializedName("total")
    private int total;

    @SerializedName("sort")
    private String sort;

    public Genre getGenre() { return genre; }
    public List<Song> getSongs() { return songs != null ? songs : new ArrayList<>(); }
    public int getTotal() { return total; }
    public String getSort() { return sort; }
}
