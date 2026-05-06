package com.example.aiagent.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Représente un fragment de connaissance extrait d'un PDF ou Google Doc
 * déposé par le client dans son Drive. Ghost stocke ces chunks en base
 * pour les injecter comme contexte dans les prompts IA.
 */
@Entity
@Table(name = "knowledge_chunks")
public class KnowledgeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private String clientId;           // ex: "coach-abir"

    @Column(name = "source_name", nullable = false)
    private String sourceName;         // nom du fichier ou URL Drive

    @Column(name = "source_url")
    private String sourceUrl;          // lien Drive original

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;            // texte extrait (max 4000 chars)

    @Column(name = "indexed_at")
    private LocalDateTime indexedAt;

    @Column(name = "file_type")
    private String fileType;           // "PDF" ou "GDOC"

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getIndexedAt() { return indexedAt; }
    public void setIndexedAt(LocalDateTime indexedAt) { this.indexedAt = indexedAt; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
}
