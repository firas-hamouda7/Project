package com.example.aiagent.core.plan;

import com.example.aiagent.core.creation.CreationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PlanningService {

    private final CreationService creation;

    @Autowired
    public PlanningService(CreationService creation) {
        this.creation = creation;
    }

    public String generatePublicityPlan(String prompt) {
        return creation.generateText(
            "Tu es un expert en stratégie publicitaire et marketing digital.",
            prompt
        );
    }

    public String generateProductPlan(String prompt) {
        return creation.generateText(
            "Tu es un expert en stratégie produit et product management.",
            prompt
        );
    }

    public PlanResult generateCompletePlan(String prompt) {
        String publicityPlan = generatePublicityPlan(prompt);
        String productPlan = generateProductPlan(prompt);
        return new PlanResult(publicityPlan, productPlan);
    }

    public static class PlanResult {
        private final String publicityPlan;
        private final String productPlan;

        public PlanResult(String publicityPlan, String productPlan) {
            this.publicityPlan = publicityPlan;
            this.productPlan = productPlan;
        }

        public String getPublicityPlan() { return publicityPlan; }
        public String getProductPlan() { return productPlan; }

        @Override
        public String toString() {
            return "=== PLAN PUBLICITÉ ===\n" + publicityPlan + "\n\n=== PLAN PRODUIT ===\n" + productPlan;
        }
    }
}
