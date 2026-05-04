package com.example.aiagent.core.creation;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CreationService {

    private final ChatModel chatModel;

    @Value("${pixabay.api.key}")
    private String pixabayApiKey;

    public CreationService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String generateText(String systemPrompt, String userPrompt) {
        Message system = new SystemMessage(systemPrompt);
        Message user = new UserMessage(userPrompt);
        Prompt p = new Prompt(List.of(system, user));
        return chatModel.call(p).getResult().getOutput().getContent();
    }

    public String generateImage(String visualTopic) {
        try {
            // visualTopic est déjà "mot1,mot2" extrait par DeepSeek dans RootController
            // On prend le premier mot uniquement pour Pixabay
            String keyword = visualTopic.split(",")[0].trim().toLowerCase().replaceAll("[^a-z]", "");
            if (keyword.isEmpty()) keyword = "food";
            System.out.println("DEBUG: Pixabay keyword = " + keyword);
            String encoded = java.net.URLEncoder.encode(keyword, java.nio.charset.StandardCharsets.UTF_8);
            String apiUrl = "https://pixabay.com/api/?key=" + pixabayApiKey
                + "&q=" + encoded
                + "&image_type=photo&per_page=5&safesearch=true&order=popular";

            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(apiUrl))
                .build();
            java.net.http.HttpResponse<String> response = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());

            String body = response.body();
            System.out.println("DEBUG: Pixabay response status = " + response.statusCode());

            // Utiliser webformatURL (toujours accessible, 640px)
            int idx = body.indexOf("\"webformatURL\":");
            if (idx != -1) {
                String sub = body.substring(idx + 15);
                int start = sub.indexOf('"') + 1;
                int end = sub.indexOf('"', start);
                String url = sub.substring(start, end).replace("\\/", "/");
                System.out.println("DEBUG: Pixabay imageUrl = " + url);
                return url;
            }
            System.out.println("DEBUG: Pixabay no results for keyword: " + keyword);
        } catch (Exception e) {
            System.err.println("Pixabay error: " + e.getMessage());
        }
        return "https://picsum.photos/1080/1080";
    }

    public String generateVideo(String visualTopic) {
        try {
            // visualTopic est déjà "mot1,mot2" extrait par DeepSeek dans RootController
            String keyword = visualTopic.split(",")[0].trim().toLowerCase().replaceAll("[^a-z]", "");
            if (keyword.isEmpty()) keyword = "food";
            System.out.println("DEBUG: Pixabay video keyword = " + keyword);

            String encoded = java.net.URLEncoder.encode(keyword, java.nio.charset.StandardCharsets.UTF_8);
            String apiUrl = "https://pixabay.com/api/videos/?key=" + pixabayApiKey
                + "&q=" + encoded
                + "&per_page=5&safesearch=true&order=popular";

            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(apiUrl))
                .build();
            java.net.http.HttpResponse<String> response = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());

            String body = response.body();
            System.out.println("DEBUG: Pixabay video status = " + response.statusCode());

            // Extraire videos.medium.url (qualité optimale)
            int idx = body.indexOf("\"medium\":");
            if (idx != -1) {
                String sub = body.substring(idx);
                int urlIdx = sub.indexOf("\"url\":");
                if (urlIdx != -1) {
                    String urlSub = sub.substring(urlIdx + 6);
                    int start = urlSub.indexOf('"') + 1;
                    int end = urlSub.indexOf('"', start);
                    String url = urlSub.substring(start, end).replace("\\/", "/");
                    System.out.println("DEBUG: Pixabay videoUrl = " + url);
                    return url;
                }
            }
            System.out.println("DEBUG: Pixabay no video results for: " + keyword);
        } catch (Exception e) {
            System.err.println("Pixabay video error: " + e.getMessage());
        }
        return "";
    }

    // --- RESTAURATION DES MÉTHODES MANQUANTES ---
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
