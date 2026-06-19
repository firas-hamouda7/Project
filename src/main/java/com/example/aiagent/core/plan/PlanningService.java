package com.example.aiagent.core.plan;

import com.example.aiagent.core.creation.CreationService;
import org.springframework.stereotype.Service;

@Service
public class PlanningService {

    private final CreationService creation;

    public PlanningService(CreationService creation) {
        this.creation = creation;
    }

    public String generatePublicityPlan(String prompt, java.util.Map<String,Object> metadata) {
        String requestId = metadata != null && metadata.containsKey("requestId") ? metadata.get("requestId").toString() : null;
        return creation.generateText(
            "Tu es un expert en stratégie publicitaire et marketing digital.",
            prompt,
            "PLANNING_SERVICE",
            metadata,
            requestId
        );
    }

    public String generateProductPlan(String prompt, java.util.Map<String,Object> metadata) {
        String requestId = metadata != null && metadata.containsKey("requestId") ? metadata.get("requestId").toString() : null;
        return creation.generateText(
            "Tu es un expert en stratégie produit et product management.",
            prompt,
            "PLANNING_SERVICE",
            metadata,
            requestId
        );
    }

    public PlanResult generateCompletePlan(String prompt, java.util.Map<String,Object> metadata) {
        String publicityPlan = generatePublicityPlan(prompt, metadata);
        String productPlan = generateProductPlan(prompt, metadata);
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
