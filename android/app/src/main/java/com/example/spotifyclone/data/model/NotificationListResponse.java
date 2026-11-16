package com.example.spotifyclone.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

public class NotificationListResponse {
    @SerializedName("items")
    private List<NotificationItem> items = new ArrayList<>();

    @SerializedName("totalCount")
    private int totalCount;

    @SerializedName("unreadCount")
    private int unreadCount;

    @SerializedName("page")
    private int page;

    @SerializedName("totalPages")
    private int totalPages;

    public List<NotificationItem> getItems() { return items != null ? items : new ArrayList<>(); }
    public void setItems(List<NotificationItem> items) { this.items = items; }

    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
}
