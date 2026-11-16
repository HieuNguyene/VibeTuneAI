package com.example.spotifyclone.data.model;

import com.google.gson.annotations.SerializedName;

public class MoodFlowSessionRequest {
    @SerializedName("firstTrackId")
    private String firstTrackId;

    @SerializedName("arc")
    private String arc;

    public MoodFlowSessionRequest(String firstTrackId, String arc) {
        this.firstTrackId = firstTrackId;
        this.arc = arc;
    }

    public String getFirstTrackId() { return firstTrackId; }
    public String getArc() { return arc; }
}
