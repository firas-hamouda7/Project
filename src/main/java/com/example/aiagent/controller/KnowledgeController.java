package com.example.aiagent.controller;

import com.example.aiagent.core.document.DriveWatcherService;
import com.example.aiagent.entity.KnowledgeChunk;
import com.example.aiagent.repository.KnowledgeChunkRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * API de gestion de la base de connaissance (mode "Veille Silencieuse").
 *
 * POST /api/knowledge/register     → enregistrer une source Drive à surveiller
 * POST /api/knowledge/index        → indexer immédiatement une source
 * POST /api/knowledge/import-text  → importer du texte brut (bio, notes, export CRM)
 * POST /api/knowledge/import-url   → scraper n'importe quel site web
 * GET  /api/knowledge/{clientId}   → lire les chunks indexés d'un client
 * GET  /api/knowledge/context/{clientId} → contexte IA formaté pour injection
 * GET  /api/knowledge/sources      → lister toutes les sources surveillées
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final DriveWatcherService watcher;
    private final KnowledgeChunkRepository repo;

    public KnowledgeController(DriveWatcherService watcher, KnowledgeChunkRepository repo) {
        this.watcher = watcher;
        this.repo = repo;
    }

    /** Enregistre une URL Drive pour surveillance automatique (polling 30min). */
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody Map<String, String> body) {
        String clientId = body.get("clientId");
        String driveUrl = body.get("driveUrl");
        if (clientId == null || driveUrl == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "clientId et driveUrl requis"));
        }
        watcher.registerSource(clientId, driveUrl);
        return ResponseEntity.ok(Map.of(
            "status", "SOURCE_REGISTERED",
            "clientId", clientId,
            "driveUrl", driveUrl,
            "message", "Ghost surveille maintenant cette source. Polling toutes les 30 minutes."
        ));
    }

    /** Indexe immédiatement une source Drive (sans attendre le polling). */
    @PostMapping("/index")
    public ResponseEntity<Map<String, Object>> indexNow(@RequestBody Map<String, String> body) {
        String clientId = body.get("clientId");
        String driveUrl = body.get("driveUrl");
        if (clientId == null || driveUrl == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "clientId et driveUrl requis"));
        }
        try {
            KnowledgeChunk chunk = watcher.indexNow(clientId, driveUrl);
            return ResponseEntity.ok(Map.of(
                "status", "INDEXED",
                "clientId", clientId,
                "sourceName", chunk.getSourceName(),
                "fileType", chunk.getFileType(),
                "indexedAt", chunk.getIndexedAt().toString(),
                "preview", chunk.getContent().substring(0, Math.min(200, chunk.getContent().length())) + "..."
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.ok(Map.of("status", "ALREADY_INDEXED", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Upload direct d'un fichier PDF et indexation immédiate. */
    @PostMapping(value = "/upload-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadPdf(
            @RequestParam MultipartFile file,
            @RequestParam String clientId) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Fichier PDF vide."));
        }
        String rawName = file.getOriginalFilename();
        String filename = (rawName != null && !rawName.isBlank()) ? rawName : "document.pdf";
        if (!filename.toLowerCase().endsWith(".pdf")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Seuls les fichiers PDF sont acceptés."));
        }

        try {
            // Extraction du texte via PDFBox 3.x
            String text;
            try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(file.getInputStream()))) {
                text = new PDFTextStripper().getText(doc).trim();
            }
            if (text.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Le PDF ne contient pas de texte lisible (PDF scanné ou image)."));
            }
            if (text.length() > 4000) text = text.substring(0, 4000) + "...";

            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setClientId(clientId);
            chunk.setSourceName(filename);
            chunk.setSourceUrl("upload://" + clientId + "/" + filename);
            chunk.setContent(text);
            chunk.setFileType("PDF");
            chunk.setIndexedAt(LocalDateTime.now());
            repo.save(chunk);

            return ResponseEntity.ok(Map.of(
                "status", "INDEXED",
                "clientId", clientId,
                "filename", filename,
                "characters", text.length(),
                "preview", text.substring(0, Math.min(300, text.length())) + "..."
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Erreur lecture PDF : " + e.getMessage()));
        }
    }

    /**
     * Importe du texte brut directement (accès API / export Notion / notes).
     * Body JSON : { "clientId": "...", "text": "...", "sourceName": "..." }
     */
    @PostMapping("/import-text")
    public ResponseEntity<Map<String, Object>> importText(@RequestBody Map<String, String> body) {
        String clientId  = body.get("clientId");
        String text      = body.get("text");
        String sourceName = body.getOrDefault("sourceName", "Texte importé");
        if (clientId == null || text == null || text.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "clientId et text requis"));
        }
        if (text.length() > 4000) text = text.substring(0, 4000) + "...";

        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setClientId(clientId);
        chunk.setSourceName(sourceName);
        chunk.setSourceUrl("api://" + clientId + "/" + sourceName);
        chunk.setContent(text);
        chunk.setFileType("API");
        chunk.setIndexedAt(LocalDateTime.now());
        repo.save(chunk);

        return ResponseEntity.ok(Map.of(
            "status", "INDEXED",
            "clientId", clientId,
            "sourceName", sourceName,
            "fileType", "API",
            "characters", text.length(),
            "preview", text.substring(0, Math.min(300, text.length())) + "..."
        ));
    }

    /**
     * Scrape n'importe quel site web et indexe son contenu (source "site web").
     * Body JSON : { "clientId": "...", "url": "https://..." }
     */
    @PostMapping("/import-url")
    public ResponseEntity<Map<String, Object>> importUrl(@RequestBody Map<String, String> body) {
        String clientId = body.get("clientId");
        String url      = body.get("url");
        if (clientId == null || url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "clientId et url requis"));
        }
        try {
            String text = watcher.fetchRawText(url);
            if (text.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Contenu vide ou inaccessible."));
            }
            if (text.length() > 4000) text = text.substring(0, 4000) + "...";

            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setClientId(clientId);
            chunk.setSourceName(url.length() > 60 ? url.substring(0, 60) + "..." : url);
            chunk.setSourceUrl(url);
            chunk.setContent(text);
            chunk.setFileType("WEB");
            chunk.setIndexedAt(LocalDateTime.now());
            repo.save(chunk);

            return ResponseEntity.ok(Map.of(
                "status", "INDEXED",
                "clientId", clientId,
                "sourceUrl", url,
                "fileType", "WEB",
                "characters", text.length(),
                "preview", text.substring(0, Math.min(300, text.length())) + "..."
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Liste tous les chunks indexés d'un client. */
    @GetMapping("/{clientId}")
    public ResponseEntity<List<KnowledgeChunk>> getChunks(@PathVariable String clientId) {
        return ResponseEntity.ok(repo.findByClientIdOrderByIndexedAtDesc(clientId));
    }

    /** Retourne le contexte IA complet d'un client (à injecter dans les prompts). */
    @GetMapping("/context/{clientId}")
    public ResponseEntity<Map<String, String>> getContext(@PathVariable String clientId) {
        String context = watcher.buildContext(clientId);
        if (context.isBlank()) {
            return ResponseEntity.ok(Map.of("status", "EMPTY", "message", "Aucune connaissance indexée pour " + clientId));
        }
        return ResponseEntity.ok(Map.of("status", "OK", "clientId", clientId, "context", context));
    }

    /** Liste toutes les sources en cours de surveillance. */
    @GetMapping("/sources")
    public ResponseEntity<Map<String, Object>> getSources() {
        return ResponseEntity.ok(Map.of(
            "watchedSources", watcher.getWatchedSources(),
            "totalClients", watcher.getWatchedSources().size()
        ));
    }
}
