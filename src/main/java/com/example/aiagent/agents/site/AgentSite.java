package com.example.aiagent.agents.site;

import com.example.aiagent.core.creation.CreationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
@Lazy
public class AgentSite {

    private final CreationService creation;

    @Autowired
    public AgentSite(CreationService creation) {
        this.creation = creation;
    }

    public String buildSite(String request, String type, Map<String, Object> metadata) {
        String systemPrompt = "Tu es un développeur web expert. "
            + "Génère le code HTML/CSS complet pour un site " + type + ". "
            + "Détails techniques : " + metadata.toString() + ". "
            + "Pas d'explication, juste le code.";
        return creation.generateText(systemPrompt, request);
    }
}
