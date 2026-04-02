package com.example.aiagent.dto;

public class ChatResponse {
    private String status;
    private String agentUsed;
    private String result;

    public ChatResponse() {}

    public ChatResponse(String status, String agentUsed, String result) {
        this.status = status;
        this.agentUsed = agentUsed;
        this.result = result;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAgentUsed() { return agentUsed; }
    public void setAgentUsed(String agentUsed) { this.agentUsed = agentUsed; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
}
