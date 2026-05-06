package com.example.aiagent.core.document;

import com.example.aiagent.entity.KnowledgeChunk;
import com.example.aiagent.repository.KnowledgeChunkRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service "Veille Silencieuse" — surveille des sources Google Drive/Docs
 * et indexe automatiquement leur contenu en base (table knowledge_chunks).
 *
 * Polling automatique toutes les 30 minutes via @Scheduled.
 */
@Service
public class DriveWatcherService {

    private final KnowledgeChunkRepository repo;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    // Map<clientId, List<driveUrl>> — sources enregistrées en mémoire
    private final Map<String, List<String>> watchedSources = new ConcurrentHashMap<>();

    public DriveWatcherService(KnowledgeChunkRepository repo) {
        this.repo = repo;
    }

    /** Enregistre une URL Drive pour surveillance automatique. */
    public void registerSource(String clientId, String driveUrl) {
        watchedSources.computeIfAbsent(clientId, k -> new ArrayList<>()).add(driveUrl);
    }

    /** Indexe immédiatement une source sans attendre le polling. */
    public KnowledgeChunk indexNow(String clientId, String driveUrl) throws Exception {
        if (repo.existsBySourceUrl(driveUrl)) {
            throw new IllegalStateException("Source déjà indexée : " + driveUrl);
        }
        return fetchAndSave(clientId, driveUrl);
    }

    /** Polling automatique toutes les 30 minutes. */
    @Scheduled(fixedDelay = 30 * 60 * 1000)
    public void pollAll() {
        for (Map.Entry<String, List<String>> entry : watchedSources.entrySet()) {
            String clientId = entry.getKey();
            for (String url : entry.getValue()) {
                if (!repo.existsBySourceUrl(url)) {
                    try {
                        KnowledgeChunk chunk = fetchAndSave(clientId, url);
                        System.out.println("[Ghost Watcher] Nouveau document indexé pour " + clientId + " : " + chunk.getSourceName());
                    } catch (Exception e) {
                        System.err.println("[Ghost Watcher] Erreur indexation " + url + " : " + e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Construit un contexte IA formaté à partir de tous les chunks d'un client.
     * Ce contexte est injecté dans les prompts des agents.
     */
    public String buildContext(String clientId) {
        List<KnowledgeChunk> chunks = repo.findByClientIdOrderByIndexedAtDesc(clientId);
        if (chunks.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("=== CONNAISSANCES INDEXÉES POUR ").append(clientId.toUpperCase()).append(" ===\n\n");
        for (KnowledgeChunk chunk : chunks) {
            sb.append("--- Source : ").append(chunk.getSourceName())
              .append(" [").append(chunk.getFileType()).append("] ---\n");
            sb.append(chunk.getContent()).append("\n\n");
        }
        return sb.toString().trim();
    }

    /** Retourne toutes les sources surveillées. */
    public Map<String, List<String>> getWatchedSources() {
        return watchedSources;
    }

    /** Scrape publiquement n'importe quelle URL et retourne le texte brut. */
    public String fetchRawText(String url) throws Exception {
        return fetchText(url);
    }

    // ── Méthodes internes ──────────────────────────────────────────────────

    private KnowledgeChunk fetchAndSave(String clientId, String url) throws Exception {
        String text;
        String fileType;
        String sourceName;

        if (url.contains("drive.google.com") && url.contains("/file/")) {
            // PDF hébergé sur Drive → export direct
            String fileId = extractFileId(url);
            String exportUrl = "https://drive.google.com/uc?export=download&id=" + fileId;
            text = fetchPdfText(exportUrl);
            fileType = "PDF";
            sourceName = "GoogleDrive-" + fileId + ".pdf";
        } else if (url.contains("docs.google.com/document")) {
            // Google Doc → export texte brut
            String fileId = extractFileId(url);
            String exportUrl = "https://docs.google.com/document/d/" + fileId + "/export?format=txt";
            text = fetchText(exportUrl);
            fileType = "GDOC";
            sourceName = "GoogleDoc-" + fileId;
        } else {
            // URL générique → scraping texte brut
            text = fetchText(url);
            fileType = "WEB";
            sourceName = url.length() > 60 ? url.substring(0, 60) + "..." : url;
        }

        if (text.isBlank()) throw new RuntimeException("Contenu vide ou inaccessible : " + url);
        if (text.length() > 4000) text = text.substring(0, 4000) + "...";

        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setClientId(clientId);
        chunk.setSourceName(sourceName);
        chunk.setSourceUrl(url);
        chunk.setContent(text);
        chunk.setFileType(fileType);
        chunk.setIndexedAt(LocalDateTime.now());
        return repo.save(chunk);
    }

    private String fetchText(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 400) throw new RuntimeException("HTTP " + res.statusCode());
        return res.body().trim();
    }

    private String fetchPdfText(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .GET().build();
        HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() >= 400) throw new RuntimeException("HTTP " + res.statusCode());
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(res.body()))) {
            return new PDFTextStripper().getText(doc).trim();
        }
    }

    private String extractFileId(String url) {
        // Extrait l'ID depuis les URLs Drive : /d/ID/ ou id=ID
        if (url.contains("/d/")) {
            String after = url.substring(url.indexOf("/d/") + 3);
            return after.contains("/") ? after.substring(0, after.indexOf("/")) : after;
        }
        if (url.contains("id=")) {
            String after = url.substring(url.indexOf("id=") + 3);
            return after.contains("&") ? after.substring(0, after.indexOf("&")) : after;
        }
        return url;
    }
}
