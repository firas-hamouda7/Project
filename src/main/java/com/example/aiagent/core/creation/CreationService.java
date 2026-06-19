package com.example.aiagent.core.creation;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.Message;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CreationService {

    private final ChatModel chatModel;
    private final MeterRegistry meterRegistry;

    @Value("${pixabay.api.key}")
    private String pixabayApiKey;

    public CreationService(ChatModel chatModel, MeterRegistry meterRegistry) {
        this.chatModel = chatModel;
        this.meterRegistry = meterRegistry;
    }

    // Simple in-memory cache and in-flight deduplication
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes
    private final ConcurrentHashMap<String, CompletableFuture<String>> inFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedValue> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> callCount = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> tokenCount = new ConcurrentHashMap<>();

    /** Counts how many DeepSeek calls are currently in-flight (concurrent). */
    private final AtomicInteger concurrentCalls = new AtomicInteger(0);

    private static class CachedValue {
        final String value;
        final long ts;
        CachedValue(String value, long ts) { this.value = value; this.ts = ts; }
        boolean isExpired() { return System.currentTimeMillis() - ts > CACHE_TTL_MS; }
    }

    private static final Path API_LOG = Paths.get("api.txt");
    private static final DateTimeFormatter LOG_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * Appends a structured line to api.txt.
     * Format: [timestamp] [thread] [concurrent=N] MESSAGE
     */
    private void logToFile(String message) {
        try {
            String timestamp = LocalDateTime.now().format(LOG_FMT);
            String thread    = Thread.currentThread().getName();
            int concurrent   = concurrentCalls.get();
            String logLine   = String.format("[%s] [thread=%s] [concurrent=%d] %s%n",
                    timestamp, thread, concurrent, message);
            Files.writeString(API_LOG, logLine, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            System.err.println("Failed to write to api.txt: " + e.getMessage());
        }
    }

    /**
     * Backwards-compatible wrapper (no agent/metadata/requestId). Generates text and uses cache/dedup.
     */
    public String generateText(String systemPrompt, String userPrompt) {
        return generateText(systemPrompt, userPrompt, "UNKNOWN", null, null);
    }

    /**
     * Primary generateText method with observability, dedup and cache. requestId may be provided by frontend.
     */
    public String generateText(String systemPrompt, String userPrompt, String agentType, Map<String,Object> metadata, String requestId) {
        String rid = requestId != null ? requestId : UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();
        String stableMeta = metadata != null
            ? metadata.entrySet().stream()
                .filter(e -> !List.of("requestId", "agentType", "videoUrl", "imageUrl", "logoUrl").contains(e.getKey()))
                .map(e -> e.getKey() + "=" + e.getValue())
                .sorted()
                .collect(Collectors.joining("|"))
            : "";
        String cacheKey = Integer.toHexString((systemPrompt + "||" + userPrompt + "||" + agentType + "||" + stableMeta).hashCode());

        // Cache lookup
        CachedValue cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            System.out.println("DEBUG: [DeepSeek] CACHE HIT requestId=" + rid + " key=" + cacheKey + " agent=" + agentType);
            meterRegistry.counter("ghost_employer_deepseek_cache_hits_total", "agent", agentType).increment();
            return cached.value;
        }

        String inFlightKey = "KEY:" + cacheKey;

        try {
            CompletableFuture<String> future = inFlight.computeIfAbsent(inFlightKey, k -> CompletableFuture.supplyAsync(() -> {
                int activeCalls = concurrentCalls.incrementAndGet();
                long callStart = System.currentTimeMillis();
                logToFile(String.format(
                    ">>> CALL START | requestId=%s | agent=%s | cacheKey=%s | activeCalls=%d",
                    rid, agentType, cacheKey, activeCalls));
                System.out.println("DEBUG: [DeepSeek] CALL START requestId=" + rid + " agent=" + agentType + " key=" + cacheKey + " activeCalls=" + activeCalls);
                try {
                    Message system = new SystemMessage(systemPrompt);
                    Message user = new UserMessage(userPrompt);
                    Prompt p = new Prompt(List.of(system, user));
                    String result = chatModel.call(p).getResult().getOutput().getContent();
                    long callEnd = System.currentTimeMillis();
                    int approxTokens = Math.max(1, (systemPrompt.length() + userPrompt.length()) / 4);
                    logToFile(String.format(
                        "<<< CALL END   | requestId=%s | agent=%s | durationMs=%d | approxTokens=%d | activeCalls=%d",
                        rid, agentType, (callEnd - callStart), approxTokens, concurrentCalls.get()));
                    System.out.println("DEBUG: [DeepSeek] CALL END requestId=" + rid + " durationMs=" + (callEnd - callStart) + " approxTokens=" + approxTokens + " agent=" + agentType);
                    meterRegistry.counter("ghost_employer_deepseek_calls_total", "agent", agentType, "status", "success").increment();
                    meterRegistry.timer("ghost_employer_deepseek_call_duration_ms", "agent", agentType).record(callEnd - callStart, TimeUnit.MILLISECONDS);
                    meterRegistry.counter("ghost_employer_deepseek_tokens_total", "agent", agentType).increment(approxTokens);
                    cache.put(cacheKey, new CachedValue(result, System.currentTimeMillis()));
                    callCount.merge(rid, 1, (a, b) -> a + b);
                    tokenCount.merge(rid, approxTokens, (a, b) -> a + b);
                    return result;
                } finally {
                    concurrentCalls.decrementAndGet();
                }
            }));

            String response = future.get();
            long endTime = System.currentTimeMillis();
            System.out.println("DEBUG: [DeepSeek] requestId=" + rid + " totalDurationMs=" + (endTime - startTime) + " agent=" + agentType + " cacheKey=" + cacheKey);
            meterRegistry.counter("ghost_employer_deepseek_requests_total", "agent", agentType).increment();
            return response;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } finally {
            inFlight.remove(inFlightKey);
        }
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
    public CreationResult generateFullContent(String systemContext, String userRequest, Map<String,Object> metadata, String requestId) {
        String text = generateText(systemContext, userRequest, "CREATION_FULL", metadata, requestId);
        String image = "";
        String video = "";
        boolean generateMedia = metadata != null && Boolean.parseBoolean(metadata.getOrDefault("generateMedia", "false").toString());
        String visualTopic = metadata != null && metadata.containsKey("visualTopic") ? metadata.get("visualTopic").toString() : "";
        if (generateMedia && visualTopic != null && !visualTopic.isBlank()) {
            // Only call image/video if visualTopic provided and media requested
            image = generateImage(visualTopic);
            video = generateVideo(visualTopic);
        }
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

    // Observability helpers
    public int getCallCount(String requestId) {
        return callCount.getOrDefault(requestId, 0);
    }

    public void clearCallCount(String requestId) {
        callCount.remove(requestId);
        tokenCount.remove(requestId);
    }

    public int getEstimatedTokens(String requestId) {
        return tokenCount.getOrDefault(requestId, 0);
    }
}
