package com.example.spotifyclone.data.model;

import com.google.gson.annotations.SerializedName;

public class VerifyOtpRequest {
    @SerializedName("phoneNumber")
    private String phoneNumber;

    @SerializedName("otp")
    private String otp;

    @SerializedName("displayName")
    private String displayName;

    @SerializedName("password")
    private String password;

    public VerifyOtpRequest(String phoneNumber, String otp, String displayName, String password) {
        this.phoneNumber = phoneNumber;
        this.otp = otp;
        this.displayName = displayName;
        this.password = password;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
