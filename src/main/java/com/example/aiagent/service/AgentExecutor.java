package com.example.aiagent.service;

import com.example.aiagent.dto.ChatResponse;
import java.util.Map;

public interface AgentExecutor {
    ChatResponse execute(Map<String, Object> payload);
    String getActionType();
}
