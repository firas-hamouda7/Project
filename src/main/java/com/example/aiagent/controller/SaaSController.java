package com.example.aiagent.controller;

import com.example.aiagent.dto.SaaSRequest;
import com.example.aiagent.dto.ChatResponse;
import com.example.aiagent.decision.DecisionEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/saas")
@CrossOrigin("*")
public class SaaSController {

    private final DecisionEngine decisionEngine;

    @Autowired
    public SaaSController(DecisionEngine decisionEngine) {
        this.decisionEngine = decisionEngine;
    }

    @PostMapping("/execute")
    public ChatResponse handleRequest(@RequestBody SaaSRequest request) {
        // La communication se contente d'envoyer la requête brute au moteur de décision
        return decisionEngine.process(request);
    }
}
