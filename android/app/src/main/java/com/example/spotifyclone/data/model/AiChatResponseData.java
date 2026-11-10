package com.example.spotifyclone.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class AiChatResponseData {
    @SerializedName("reply")
    private String reply;

    @SerializedName("suggestedSongs")
    private List<Song> suggestedSongs;

    @SerializedName("suggestedReplies")
    private List<String> suggestedReplies;

    @SerializedName("provider")
    private String provider;

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
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

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }
}
