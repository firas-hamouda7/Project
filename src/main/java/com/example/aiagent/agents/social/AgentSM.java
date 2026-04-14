package com.example.aiagent.agents.social;

import com.example.aiagent.core.creation.CreationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
@Lazy
public class AgentSM {

    private final CreationService creation;

    @Autowired
    public AgentSM(CreationService creation) {
        this.creation = creation;
    }

    public String processSocialMedia(String request, String platform, Map<String, Object> metadata) {
        String systemPrompt = "Tu es un Strategiste Social Media de haut niveau spécialisé dans la viralité et l'engagement. "
            + "1. Adapte STRICTEMENT ton style à la plateforme : " + platform + ". "
            + "2. Pour chaque post, inclus : Un [Hook] puissant, le [Corps du post] avec emojis, et les [Hashtags] stratégiques. "
            + "3. Propose une 'Suggestion Visuelle' (description de l'image ou vidéo à utiliser). "
            + "4. Consignes stratégiques : " + metadata.toString() + ". "
            + "Ton but est de créer du contenu qui s'arrête le scroll et génère des partages.";
            
        return creation.generateText(systemPrompt, request);
    }
}
