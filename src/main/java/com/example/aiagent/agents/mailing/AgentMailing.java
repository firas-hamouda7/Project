package com.example.aiagent.agents.mailing;

import com.example.aiagent.core.creation.CreationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
@Lazy
public class AgentMailing {

    private final CreationService creation;

    @Autowired
    public AgentMailing(CreationService creation) {
        this.creation = creation;
    }

    public String processMailing(String request, Map<String, Object> metadata) {
        String systemPrompt = "Tu es un expert en email marketing. "
            + "Métadonnées de ton/cible/objectifs : " + metadata.toString() + ". "
            + "Génère un email professionnel complet qui respecte ces paramètres.";
        return creation.generateText(systemPrompt, request);
    }
}
