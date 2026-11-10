package com.example.spotifyclone.data.model;

import java.util.ArrayList;
import java.util.List;

public class AiChatMessage {
    public static final int TYPE_USER = 1;
    public static final int TYPE_AI = 2;
    public static final int TYPE_LOADING = 3;

    private int type;
    private String message;
    private List<Song> suggestedSongs;
    private List<String> suggestedReplies;
    private long timestamp;

    public AiChatMessage(int type, String message) {
        this.type = type;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
        this.suggestedSongs = new ArrayList<>();
        this.suggestedReplies = new ArrayList<>();
    }

    public AiChatMessage(int type, String message, List<Song> suggestedSongs, List<String> suggestedReplies) {
        this.type = type;
        this.message = message;
        this.suggestedSongs = suggestedSongs != null ? suggestedSongs : new ArrayList<>();
        this.suggestedReplies = suggestedReplies != null ? suggestedReplies : new ArrayList<>();
        this.timestamp = System.currentTimeMillis();
    }

    public static AiChatMessage createLoading() {
        return new AiChatMessage(TYPE_LOADING, "");
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<Song> getSuggestedSongs() {
        return suggestedSongs;
    }

    public void setSuggestedSongs(List<Song> suggestedSongs) {
        this.suggestedSongs = suggestedSongs;
    }

    public List<String> getSuggestedReplies() {
        return suggestedReplies;
    }

    public void setSuggestedReplies(List<String> suggestedReplies) {
        this.suggestedReplies = suggestedReplies;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
