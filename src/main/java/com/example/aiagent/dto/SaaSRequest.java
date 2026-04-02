package com.example.aiagent.dto;

import java.util.Map;

public class SaaSRequest {
    private String actionType;
    private Map<String, Object> payload;

    public SaaSRequest() {}

    public SaaSRequest(String actionType, Map<String, Object> payload) {
        this.actionType = actionType;
        this.payload = payload;
    }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }
}
