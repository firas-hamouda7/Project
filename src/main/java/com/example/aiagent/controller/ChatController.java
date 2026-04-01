package com.example.aiagent.controller;

import com.example.aiagent.entity.ChatHistory;
import com.example.aiagent.repository.ChatHistoryRepository;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final OllamaChatModel chatModel;
    private final ChatHistoryRepository chatHistoryRepository;

    @Autowired
    public ChatController(OllamaChatModel chatModel, ChatHistoryRepository chatHistoryRepository) {
        this.chatModel = chatModel;
        this.chatHistoryRepository = chatHistoryRepository;
    }

    @GetMapping("/hello")
    public String helloWorld() {
        String userMessage = "Hello World!";
        // Appelle le modèle d'IA pour obtenir une réponse
        String aiResponse = chatModel.call(userMessage);

        // Sauvegarde dans la base de données
        ChatHistory history = new ChatHistory(userMessage, aiResponse);
        chatHistoryRepository.save(history);

        return aiResponse;
    }

    @GetMapping("/ask")
    public String ask(@RequestParam(defaultValue = "Hello") String message) {
        String aiResponse = chatModel.call(message);

        // Sauvegarde dans la base de données
        ChatHistory history = new ChatHistory(message, aiResponse);
        chatHistoryRepository.save(history);

        return aiResponse;
    }
}
