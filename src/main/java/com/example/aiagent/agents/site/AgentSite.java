package com.example.aiagent.agents.site;

import java.util.Map;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.example.aiagent.core.creation.CreationService;

@Service
@Lazy
public class AgentSite {

    private final CreationService creation;

    public AgentSite(CreationService creation) {
        this.creation = creation;
    }

    public String buildSite(String request, String type, Map<String, Object> metadata) {
        String language = metadata.getOrDefault("language", "fr").toString();
        String typeParam = metadata.getOrDefault("type", "").toString();
        String lowerRequest = request.toLowerCase();

        String systemPrompt;

        // CAS 1 : Contenu texte (explicitement choisi ou mot-clé)
        if (typeParam.equals("contenu") || (!typeParam.equals("landing_page") && (lowerRequest.contains("contenu") || lowerRequest.contains("plan")))) {
            systemPrompt = "Tu es un Expert en Stratégie Marketing. "
                + "Ta mission est de rédiger UNIQUEMENT le contenu textuel détaillé pour une landing page en " + language + ". "
                + "Donne des idées, des titres accrocheurs et le plan marketing. "
                + "INTERDICTION DE RÉPONDRE EN HTML. RÉPONDS UNIQUEMENT EN TEXTE.";
        }
        // CAS 2 : Landing page HTML (par défaut ou choix explicite)
        else {
            systemPrompt = "Tu es une Designer UI/UX de renommée mondiale. "
                + "CONSIGNE CRUCIALE : "
                + "1. Utilise Tailwind CSS via CDN pour le style. "
                + "2. Crée un design moderne et haut de gamme. "
                + "3. Écris tout le contenu en " + language + ". "
                + "Détails Projet : " + metadata.toString() + ". "
                + "RENVOIE UNIQUEMENT LE CODE HTML COMPLET (<!DOCTYPE html>...</html>). PAS DE TEXTE AUTOUR. PAS DE MARKDOWN. PAS DE BACKTICKS.";
        }
            
        return creation.generateText(systemPrompt, request);
    }
}
