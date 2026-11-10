package com.example.spotifyclone.data.model;

import com.google.gson.annotations.SerializedName;

public class SendOtpRequest {
    @SerializedName("phoneNumber")
    private String phoneNumber;

    public SendOtpRequest(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}
