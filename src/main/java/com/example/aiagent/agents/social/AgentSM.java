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
        String systemPrompt = "Tu es un expert en community management pour " + platform + ". "
            + "Considère ces métadonnées : " + metadata.toString() + ". "
            + "Crée un post engageant avec le bon ton et les bons hashtags.";
        return creation.generateText(systemPrompt, request);
    }
}
