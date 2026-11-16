package com.example.spotifyclone.data.model;

import com.google.gson.annotations.SerializedName;

public class UnreadCountData {
    @SerializedName("unreadCount")
    private int unreadCount;

    public UnreadCountData() {}

    public UnreadCountData(int unreadCount) {
        this.unreadCount = unreadCount;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }
}
