package com.example.aiagent.agents.social;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import com.example.aiagent.core.creation.CreationService;

@Service
@Lazy
public class AgentSM {

    private final CreationService creation;

    @Autowired
    public AgentSM(CreationService creation) {
        this.creation = creation;
    }

    public String processSocialMedia(String request, String platform, Map<String, Object> metadata) {
        String language = metadata.getOrDefault("language", "fr").toString();
        String targetPlatform = metadata.getOrDefault("platform", platform).toString();
        
        String styleInstruction = switch (targetPlatform.toLowerCase()) {
            case "linkedin" -> "Utilise un ton professionnel, expert et structuré (B2B). Pas d'excès d'émojis.";
            case "facebook" -> "Utilise un ton chaleureux, communautaire et engageant. Encourage les commentaires.";
            case "twitter", "x" -> "Sois très court, punchy et utilise des tags de tendances.";
            default -> "Sois visuel, moderne et utilise des émojis (Style Instagram).";
        };

        String systemPrompt = "Tu es un Strategiste Social Media spécialisé sur la plateforme " + targetPlatform + ". "
            + "TA MISSION : " + styleInstruction
            + "\n1. Langue : " + language
            + "\n2. SOIS MINIMALISTE ET PUNCHY."
            + "\n3. RENVOIE UNIQUEMENT LA PUBLICATION FINALE.";

        return creation.generateText(systemPrompt, request);
    }
}
