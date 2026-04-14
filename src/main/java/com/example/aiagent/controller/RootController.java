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
import org.springframework.http.MediaType;
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

        if (lower.contains("site") || lower.contains("page") || lower.contains("landing")) {
            aiResult = agentSite.buildSite(prompt, "LP", metadata);
            agentType = "AGENT_SITE";
        } else if (lower.contains("post") || lower.contains("social") || lower.contains("meta") || lower.contains("tweet")) {
            aiResult = agentSM.processSocialMedia(prompt, "Digital Platform", metadata);
            agentType = "AGENT_SM";
        } else if (lower.contains("mail") || lower.contains("email") || lower.contains("newsletter")) {
            aiResult = agentMailing.processMailing(prompt, metadata);
            agentType = "AGENT_MAILING";
        } else {
            aiResult = "Agent non déterminé pour cette demande.";
            agentType = "UNKNOWN_AGENT";
        }

        // NETTOYAGE DU CODE (Enlève les balises ```html et ```)
        aiResult = cleanAiResponse(aiResult);

        results.add(new ChatResponse.ProcessedResult(agentType, aiResult, Map.of("prompt", prompt)));
        saveHistory(prompt, aiResult, agentType, metadata);

        return new ChatResponse("SUCCESS", results);
    }

    private String cleanAiResponse(String source) {
        if (source == null) return "";
        // 1. Enlever les balises Markdown
        String cleaned = source.replace("```html", "")
                               .replace("```", "")
                               .trim();
        
        // 2. Couper tout ce qui dépasse après la fin du code HTML
        int htmlEnd = cleaned.toLowerCase().lastIndexOf("</html>");
        if (htmlEnd != -1) {
            cleaned = cleaned.substring(0, htmlEnd + 7);
        }
        
        return cleaned.trim();
    }

    @GetMapping(value = "/preview", produces = MediaType.TEXT_HTML_VALUE)
    public String previewLatest() {
        try {
            return chatHistoryRepository.findAll().stream()
                .filter(h -> h != null && h.getAgentType() != null && h.getAgentType().startsWith("AGENT_SITE"))
                .reduce((first, second) -> second)
                .map(ChatHistory::getAiResponse)
                .orElse("<html><body><h1>Aucun historique</h1></body></html>");
        } catch (Exception e) {
            return "<html><body><h1>Erreur</h1><p>" + e.getMessage() + "</p></body></html>";
        }
    }

    @GetMapping(value = "/preview/latest", produces = MediaType.TEXT_HTML_VALUE)
    public String previewLatestAlias() {
        return previewLatest();
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
