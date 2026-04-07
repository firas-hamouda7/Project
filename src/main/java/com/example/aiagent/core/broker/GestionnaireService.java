package com.example.aiagent.core.broker;

import com.example.aiagent.core.creation.CreationService;
import com.example.aiagent.core.plan.PlanningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GestionnaireService {

    private final CreationService creation;
    private final PlanningService plan;

    @Autowired
    public GestionnaireService(CreationService creation, PlanningService plan) {
        this.creation = creation;
        this.plan = plan;
    }

    public String processPublicite(String request) {
        String content = creation.generateText("Tu es un expert marketing.", request);
        String publicityPlan = plan.generatePublicityPlan(request);
        return "=== CONTENU ===\n" + content + "\n\n=== PLAN PUB ===\n" + publicityPlan;
    }

    public String processProduit(String request) {
        String content = creation.generateText("Tu es un expert produit.", request);
        String productPlan = plan.generateProductPlan(request);
        return "=== CONTENU ===\n" + content + "\n\n=== PLAN PROD ===\n" + productPlan;
    }

    public String processComplete(String request) {
        CreationService.CreationResult fullContent = creation.generateFullContent("Stratégie digitale.", request);
        PlanningService.PlanResult plans = plan.generateCompletePlan(request);
        return "=== CONTENU ===\n" + fullContent.getText() + "\n\n=== PLANS ===\n" + plans.toString();
    }
}
