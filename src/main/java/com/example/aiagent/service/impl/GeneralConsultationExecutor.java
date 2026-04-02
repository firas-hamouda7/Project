package com.example.aiagent.service.impl;

import com.example.aiagent.dto.ChatResponse;
import com.example.aiagent.service.AgentExecutor;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class GeneralConsultationExecutor implements AgentExecutor {

    private final OllamaChatModel chatModel;

    @Autowired
    public GeneralConsultationExecutor(OllamaChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public ChatResponse execute(Map<String, Object> payload) {
        String query = (String) payload.get("query");
        String result = chatModel.call(query);
        return new ChatResponse("SUCCESS", getActionType(), result);
    }

    @Override
    public String getActionType() {
        return "GENERAL_CONSULTATION";
    }
}
