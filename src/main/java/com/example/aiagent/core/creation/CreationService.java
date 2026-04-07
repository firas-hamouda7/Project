package com.example.aiagent.core.creation;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CreationService {

    private final OllamaChatModel chatModel;

    @Autowired
    public CreationService(OllamaChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String generateText(String prompt) {
        return chatModel.call(prompt);
    }

    public String generateText(String systemPrompt, String userPrompt) {
        Message system = new SystemMessage(systemPrompt);
        Message user = new UserMessage(userPrompt);
        Prompt p = new Prompt(List.of(system, user));
        return chatModel.call(p).getResult().getOutput().getContent();
    }

    public String generateImage(String prompt) {
        return "[IMAGE_ASSET | prompt=" + prompt.hashCode() + "]";
    }

    public String generateVideo(String prompt) {
        return "[VIDEO_ASSET | prompt=" + prompt.hashCode() + "]";
    }

    public CreationResult generateFullContent(String systemContext, String userRequest) {
        String text = generateText(systemContext, userRequest);
        String image = generateImage(userRequest);
        String video = generateVideo(userRequest);
        return new CreationResult(text, image, video);
    }

    public static class CreationResult {
        private final String text;
        private final String image;
        private final String video;

        public CreationResult(String text, String image, String video) {
            this.text = text;
            this.image = image;
            this.video = video;
        }

        public String getText() { return text; }
        public String getImage() { return image; }
        public String getVideo() { return video; }

        @Override
        public String toString() {
            return "=== TEXTE ===\n" + text + "\n\n=== IMAGE ===\n" + image + "\n\n=== VIDÉO ===\n" + video;
        }
    }
}
