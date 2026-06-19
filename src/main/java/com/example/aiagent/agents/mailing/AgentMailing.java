package com.example.aiagent.agents.mailing;

import java.util.Map;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.example.aiagent.core.creation.CreationService;

@Service
@Lazy
public class AgentMailing {

    private final CreationService creation;

    public AgentMailing(CreationService creation) {
        this.creation = creation;
    }

    public String processMailing(String request, Map<String, Object> metadata) {
        String language = metadata.getOrDefault("language", "Français").toString();
        String systemPrompt = "Tu es un Expert en Email Copywriting spécialisé dans la conversion et le Storytelling. "
            + "LANGUE DE RÉDACTION : " + language + " (OBLIGATOIRE — rédige TOUT le contenu en " + language + "). "
            + "1. Utilise des structures d'écriture puissantes (AIDA : Attention, Intérêt, Désir, Action). "
            + "2. Propose toujours 3 variantes d'objets (Open-rate optimized). "
            + "3. Format : Utilise du Markdown pour une lecture élégante (gras, listes, séparation). "
            + "4. Incorpore naturellement les métadonnées suivantes : " + metadata.toString() + ". "
            + "Ton but est de maximiser le taux de clic.";
            
        String requestId = metadata != null && metadata.containsKey("requestId") ? metadata.get("requestId").toString() : null;
        return creation.generateText(systemPrompt, request, "AGENT_MAILING", metadata, requestId);
    }
}
