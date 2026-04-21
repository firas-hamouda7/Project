package com.example.aiagent.dto;

import java.util.List;
import java.util.Map;

public class ChatResponse {
    private String status;
    private String previewUrl;
    private List<ProcessedResult> detailedResults;

    public ChatResponse() {}

    public ChatResponse(String status, List<ProcessedResult> detailedResults) {
        this.status = status;
        this.detailedResults = detailedResults;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getPreviewUrl() { return previewUrl; }
    public void setPreviewUrl(String previewUrl) { this.previewUrl = previewUrl; }

    public List<ProcessedResult> getDetailedResults() { return detailedResults; }
    public void setDetailedResults(List<ProcessedResult> detailedResults) { this.detailedResults = detailedResults; }

    public static class ProcessedResult {
        private final String agentType;
        private final String response;
        private final Map<String, Object> metadata;

        public ProcessedResult(String agentType, String response, Map<String, Object> metadata) {
            this.agentType = agentType;
            this.response = response;
            this.metadata = metadata;
        }

        public String getAgentType() { return agentType; }
        public String getResponse() { return response; }
        public Map<String, Object> getMetadata() { return metadata; }
    }
}
