package com.example.aiagent.core.broker;

import com.example.aiagent.core.creation.CreationService;
import com.example.aiagent.core.plan.PlanningService;
import org.springframework.stereotype.Service;

@Service
public class GestionnaireService {

    private final CreationService creation;
    private final PlanningService plan;

    public GestionnaireService(CreationService creation, PlanningService plan) {
        this.creation = creation;
        this.plan = plan;
    }

    public String processPublicite(String request, java.util.Map<String,Object> metadata) {
        String requestId = metadata != null && metadata.containsKey("requestId") ? metadata.get("requestId").toString() : null;
        String content = creation.generateText("Tu es un expert marketing.", request, "GESTIONNAIRE", metadata, requestId);
        String publicityPlan = plan.generatePublicityPlan(request, metadata);
        return "=== CONTENU ===\n" + content + "\n\n=== PLAN PUB ===\n" + publicityPlan;
    }

    public String processProduit(String request, java.util.Map<String,Object> metadata) {
        String requestId = metadata != null && metadata.containsKey("requestId") ? metadata.get("requestId").toString() : null;
        String content = creation.generateText("Tu es un expert produit.", request, "GESTIONNAIRE", metadata, requestId);
        String productPlan = plan.generateProductPlan(request, metadata);
        return "=== CONTENU ===\n" + content + "\n\n=== PLAN PROD ===\n" + productPlan;
    }

    public String processComplete(String request, java.util.Map<String,Object> metadata) {
        String requestId = metadata != null && metadata.containsKey("requestId") ? metadata.get("requestId").toString() : null;
        CreationService.CreationResult fullContent = creation.generateFullContent("Stratégie digitale.", request, metadata, requestId);
        PlanningService.PlanResult plans = plan.generateCompletePlan(request, metadata);
        return "=== CONTENU ===\n" + fullContent.getText() + "\n\n=== PLANS ===\n" + plans.toString();
    }
}
