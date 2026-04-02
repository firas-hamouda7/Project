package com.example.aiagent.dto;

public class ChatRequest {
    private String message;
    private String requestType;

    public ChatRequest() {}

    public ChatRequest(String message, String requestType) {
        this.message = message;
        this.requestType = requestType;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }
}
