package com.example.aiagent.service;

import com.example.aiagent.dto.ChatResponse;

public interface AgentService {
    ChatResponse processRequest(String message);
    String getAgentName();
}
