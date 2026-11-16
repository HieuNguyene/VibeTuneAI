package com.example.spotifyclone.data.model;

import com.google.gson.annotations.SerializedName;

public class MoodFlowNextResponse {
    @SerializedName("sessionId")
    private String sessionId;

    @SerializedName("track")
    private Song track;

    @SerializedName("reason")
    private String reason;

    public String getSessionId() { return sessionId; }
    public Song getTrack() { return track; }
    public String getReason() { return reason; }
}
