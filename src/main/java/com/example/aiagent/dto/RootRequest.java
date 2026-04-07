package com.example.aiagent.dto;

import java.util.Map;

public class RootRequest {
    private String prompt;
    private Map<String, Object> metadata; // Utilisation de Object pour supporter les listes [a, b, c]

    public RootRequest() {}

    public RootRequest(String prompt, Map<String, Object> metadata) {
        this.prompt = prompt;
        this.metadata = metadata;
    }

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
