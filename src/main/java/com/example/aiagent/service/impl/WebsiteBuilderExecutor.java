package com.example.aiagent.service.impl;

import com.example.aiagent.dto.ChatResponse;
import com.example.aiagent.service.AgentExecutor;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class WebsiteBuilderExecutor implements AgentExecutor {

    private final OllamaChatModel chatModel;

    private static final String SYSTEM_PROMPT = """
        Tu es un moteur d'exécution de code web. Ta mission est de traduire une demande utilisateur en code HTML/CSS.
        Réponds UNIQUEMENT avec le code.
        """;

    @Autowired
    public WebsiteBuilderExecutor(OllamaChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public ChatResponse execute(Map<String, Object> payload) {
        String promptText = (String) payload.get("prompt");
        
        SystemPromptTemplate template = new SystemPromptTemplate(SYSTEM_PROMPT);
        Message systemMessage = template.createMessage();
        UserMessage userMessage = new UserMessage(promptText);

        Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
        String result = chatModel.call(prompt).getResult().getOutput().getContent();

        return new ChatResponse("SUCCESS", getActionType(), result);
    }

    @Override
    public String getActionType() {
        return "GENERATE_WEBSITE";
    }
}
