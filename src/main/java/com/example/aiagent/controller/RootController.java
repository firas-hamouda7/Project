package com.example.aiagent.controller;

import com.example.aiagent.dto.RootRequest;
import com.example.aiagent.dto.ChatResponse;
import com.example.aiagent.agents.site.AgentSite;
import com.example.aiagent.agents.social.AgentSM;
import com.example.aiagent.agents.mailing.AgentMailing;
import com.example.aiagent.entity.ChatHistory;
import com.example.aiagent.repository.ChatHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/root")
@CrossOrigin("*")
public class RootController {

    private final AgentSite agentSite;
    private final AgentSM agentSM;
    private final AgentMailing agentMailing;
    private final ChatHistoryRepository chatHistoryRepository;

    @Autowired
    public RootController(
            @Lazy AgentSite agentSite,
            @Lazy AgentSM agentSM,
            @Lazy AgentMailing agentMailing,
            ChatHistoryRepository chatHistoryRepository) {
        this.agentSite = agentSite;
        this.agentSM = agentSM;
        this.agentMailing = agentMailing;
        this.chatHistoryRepository = chatHistoryRepository;
    }

    @PostMapping("/process")
    public ChatResponse process(@RequestBody RootRequest request) {
        List<ChatResponse.ProcessedResult> results = new ArrayList<>();
        
        String prompt = request.getPrompt();
        Map<String, Object> metadata = request.getMetadata();
        String lower = prompt.toLowerCase();
        
        String aiResult;
        String agentType;

        // ROUTAGE INTELLIGENT BASÉ SUR TON PROMPT
        if (lower.contains("site") || lower.contains("page") || lower.contains("landing")) {
            // Routage → Agent Site
            aiResult = agentSite.buildSite(prompt, "LP", metadata);
            agentType = "AGENT_SITE";
        } else if (lower.contains("post") || lower.contains("social") || lower.contains("meta") || lower.contains("tweet")) {
            // Routage → Agent SM
            aiResult = agentSM.processSocialMedia(prompt, "Digital Platform", metadata);
            agentType = "AGENT_SM";
        } else if (lower.contains("mail") || lower.contains("email") || lower.contains("newsletter")) {
            // Routage → Agent Mailing
            aiResult = agentMailing.processMailing(prompt, metadata);
            agentType = "AGENT_MAILING";
        } else {
            // Résultat par défaut si non déterminé
            aiResult = "Agent non déterminé pour cette demande.";
            agentType = "UNKNOWN_AGENT";
        }

        // Ajout au résultat structuré
        results.add(new ChatResponse.ProcessedResult(agentType, aiResult, Map.of("prompt", prompt)));

        // Sauvegarde historique avec ton nouveau JSON consolidé
        saveHistory(prompt, aiResult, agentType, metadata);

        return new ChatResponse("SUCCESS", results);
    }

    private void saveHistory(String userMessage, String aiResponse, String agentType, Map<String, Object> metadataMap) {
        ChatHistory history = new ChatHistory();
        history.setUserMessage(userMessage);
        history.setAiResponse(aiResponse);
        history.setAgentType(agentType);
        if (metadataMap != null && !metadataMap.isEmpty()) {
            history.setMetadata(metadataMap.toString());
        }
        history.setTimestamp(LocalDateTime.now());
        chatHistoryRepository.save(history);
    }
}
