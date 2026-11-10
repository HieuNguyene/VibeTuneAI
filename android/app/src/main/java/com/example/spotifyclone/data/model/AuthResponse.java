package com.example.spotifyclone.data.model;

import com.google.gson.annotations.SerializedName;

public class AuthResponse {
    @SerializedName("user")
    private User user;

    @SerializedName("token")
    private String token;

    public User getUser() { return user; }
    public String getToken() { return token; }
}
