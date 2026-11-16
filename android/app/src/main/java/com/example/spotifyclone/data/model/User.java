package com.example.spotifyclone.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

public class User {
    @SerializedName("_id")
    private String id;

    @SerializedName("username")
    private String username;

    @SerializedName("email")
    private String email;

    @SerializedName("displayName")
    private String displayName;

    @SerializedName("avatarUrl")
    private String avatarUrl;

    @SerializedName("role")
    private String role;

    @SerializedName("phoneNumber")
    private String phoneNumber;

    @SerializedName("preferences")
    private Map<String, Object> preferences;

    public User() {}

    public User(String id, String username, String email, String displayName, String avatarUrl, String role) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.role = role;
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName != null ? displayName : username; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getRole() { return role; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public Map<String, Object> getPreferences() { return preferences; }
    public String getStreamingQuality() {
        if (preferences != null) {
            Object val = preferences.get("streamingQuality");
            if (val instanceof String) return (String) val;
        }
        return null;
    }
}
