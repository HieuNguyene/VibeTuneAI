package com.example.spotifyclone.data.model;

public class LyricLine {
    private final long timestampMs;
    private final String text;

    public LyricLine(long timestampMs, String text) {
        this.timestampMs = timestampMs;
        this.text = text != null ? text : "";
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public String getText() {
        return text;
    }

    public boolean hasTimestamp() {
        return timestampMs >= 0;
    }
}
