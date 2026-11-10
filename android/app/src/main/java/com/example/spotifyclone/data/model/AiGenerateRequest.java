package com.example.spotifyclone.data.model;

import com.google.gson.annotations.SerializedName;

public class AiGenerateRequest {
    @SerializedName("prompt")
    private String prompt;

    @SerializedName("savePlaylist")
    private boolean savePlaylist;

    public AiGenerateRequest(String prompt, boolean savePlaylist) {
        this.prompt = prompt;
        this.savePlaylist = savePlaylist;
    }

    public String getPrompt() { return prompt; }
    public boolean isSavePlaylist() { return savePlaylist; }
}
