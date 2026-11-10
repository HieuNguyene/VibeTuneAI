package com.example.spotifyclone.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class AiCriteria {
    @SerializedName("title")
    private String title;

    @SerializedName("description")
    private String description;

    @SerializedName("mood")
    private List<String> mood;

    @SerializedName("genres")
    private List<String> genres;

    @SerializedName("tempo")
    private String tempo;

    @SerializedName("keywords")
    private List<String> keywords;

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public List<String> getMood() { return mood; }
    public List<String> getGenres() { return genres; }
    public String getTempo() { return tempo; }
    public List<String> getKeywords() { return keywords; }
}
