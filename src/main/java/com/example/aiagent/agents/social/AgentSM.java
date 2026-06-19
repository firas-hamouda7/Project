package com.example.aiagent.agents.social;

import java.util.Map;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import com.example.aiagent.core.creation.CreationService;

@Service
@Lazy
public class AgentSM {

    private final CreationService creation;

    public AgentSM(CreationService creation) {
        this.creation = creation;
    }

    public String processSocialMedia(String request, String platform, Map<String, Object> metadata) {
        String language = metadata.getOrDefault("language", "français").toString();
        String targetPlatform = metadata.getOrDefault("platform", platform).toString();
        String branding = metadata.getOrDefault("branding", "").toString().trim();
        String type = metadata.getOrDefault("type", "post").toString().toLowerCase();
        boolean isReel = type.equals("reel") || type.equals("video") || type.equals("vidéo");

        String brandLine = branding.isBlank() ? "" : "\n- Marque : " + branding + " (intègre le nom naturellement dans le texte)";

        String styleInstruction;
        if (isReel) {
            styleInstruction = """
                Tu génères le SCRIPT TEXTE COMPLET d'un Reel Instagram viral.
                STRUCTURE OBLIGATOIRE :
                🎬 ACCROCHE (1 phrase choc pour les 3 premières secondes — question ou affirmation forte)
                📍 CORPS (3 à 5 points courts, visuels, dynamiques — chaque point sur une ligne)
                🎯 CALL TO ACTION (1 phrase finale : follow, lien en bio, commentaire)
                #️⃣ HASHTAGS (8 à 12 hashtags pertinents et populaires)
                RÈGLES : Phrases très courtes. Rythme rapide. Émojis expressifs. Ton inspirant et énergique.""";
        } else {
            styleInstruction = switch (targetPlatform.toLowerCase()) {
                case "linkedin" -> """
                    Tu génères une publication LinkedIn professionnelle.
                    STRUCTURE : Accroche forte → 3 insights clés → Conclusion + question engageante.
                    Ton expert, structuré, B2B. Pas d'excès d'émojis. Maximum 1300 caractères.""";
                case "facebook" -> """
                    Tu génères une publication Facebook engageante.
                    STRUCTURE : Accroche émotionnelle → Corps storytelling → CTA (partage/commentaire).
                    Ton chaleureux et communautaire. Émojis modérés.""";
                case "twitter", "x" -> """
                    Tu génères un tweet percutant.
                    Maximum 280 caractères. 1 seule idée forte. 2-3 hashtags tendance. Ton direct et punchy.""";
                default -> """
                    Tu génères une caption Instagram optimisée.
                    STRUCTURE : Accroche (1ère ligne visible) → Corps (2-4 lignes) → CTA → Hashtags (10-15).
                    Ton visuel, moderne, authentique. Émojis expressifs.""";
            };
        }

        String systemPrompt = "Tu es un expert Social Media spécialisé sur " + targetPlatform + " Reel."
            + "\nLANGUE DE RÉPONSE : " + language + " (OBLIGATOIRE — tout le contenu dans cette langue)"
            + brandLine
            + "\n\nMISSION :\n" + styleInstruction
            + "\n\nRÈGLE ABSOLUE : RENVOIE UNIQUEMENT LE CONTENU FINAL. Aucune explication, aucun titre comme 'Voici la publication :'.";

        String requestId = metadata != null && metadata.containsKey("requestId") ? metadata.get("requestId").toString() : null;
        return creation.generateText(systemPrompt, request, "AGENT_SM", metadata, requestId);
    }
}
