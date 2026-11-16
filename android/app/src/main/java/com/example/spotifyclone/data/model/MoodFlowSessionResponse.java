package com.example.spotifyclone.data.model;

import com.google.gson.annotations.SerializedName;

public class MoodFlowSessionResponse {
    @SerializedName("sessionId")
    private String sessionId;

    @SerializedName("arc")
    private String arc;

    @SerializedName("position")
    private int position;

    public String getSessionId() { return sessionId; }
    public String getArc() { return arc; }
    public int getPosition() { return position; }
}
