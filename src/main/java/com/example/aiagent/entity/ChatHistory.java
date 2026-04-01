package com.example.aiagent.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_history")
public class ChatHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String userMessage;

    @Column(columnDefinition = "TEXT")
    private String aiResponse;

    private LocalDateTime createdAt;

    public ChatHistory() {
        this.createdAt = LocalDateTime.now();
    }

    public ChatHistory(String userMessage, String aiResponse) {
        this.userMessage = userMessage;
        this.aiResponse = aiResponse;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getUserMessage() { return userMessage; }
    public void setUserMessage(String userMessage) { this.userMessage = userMessage; }
    public String getAiResponse() { return aiResponse; }
    public void setAiResponse(String aiResponse) { this.aiResponse = aiResponse; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
