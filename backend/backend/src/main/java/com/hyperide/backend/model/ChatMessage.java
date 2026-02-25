package com.hyperide.backend.model;

public class ChatMessage {
    private String sender;
    private String content;
    private String timestamp;

    // Getters and Setters for all 3 fields
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}