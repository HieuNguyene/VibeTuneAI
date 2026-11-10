package com.example.spotifyclone.data.model;

import com.google.gson.annotations.SerializedName;

public class SendOtpResponse {
    @SerializedName("message")
    private String message;

    @SerializedName("expiresIn")
    private int expiresIn;

    @SerializedName("devOtp")
    private String devOtp;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(int expiresIn) {
        this.expiresIn = expiresIn;
    }

    public String getDevOtp() {
        return devOtp;
    }

    public void setDevOtp(String devOtp) {
        this.devOtp = devOtp;
    }
}
