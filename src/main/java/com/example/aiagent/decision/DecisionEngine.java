package com.example.aiagent.decision;

import com.example.aiagent.dto.SaaSRequest;
import com.example.aiagent.dto.ChatResponse;
import com.example.aiagent.service.AgentExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class DecisionEngine {

    private final List<AgentExecutor> executors;

    @Autowired
    public DecisionEngine(List<AgentExecutor> executors) {
        this.executors = executors;
    }

    public ChatResponse process(SaaSRequest request) {
        // La décision est basée sur l'actionType
        return executors.stream()
                .filter(e -> e.getActionType().equalsIgnoreCase(request.getActionType()))
                .findFirst()
                .map(e -> e.execute(request.getPayload()))
                .orElseThrow(() -> new RuntimeException("Aucun agent d'exécution trouvé pour : " + request.getActionType()));
    }
}
