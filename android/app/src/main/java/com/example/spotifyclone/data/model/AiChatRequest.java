package com.example.spotifyclone.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class AiChatRequest {
    @SerializedName("message")
    private String message;

    @SerializedName("conversationHistory")
    private List<Object> conversationHistory;

    public AiChatRequest(String message) {
        this.message = message;
    }

    public AiChatRequest(String message, List<Object> conversationHistory) {
        this.message = message;
        this.conversationHistory = conversationHistory;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<Object> getConversationHistory() {
        return conversationHistory;
    }

    public void setConversationHistory(List<Object> conversationHistory) {
        this.conversationHistory = conversationHistory;
    }
}
