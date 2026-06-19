package com.example.aiagent.repository;

import com.example.aiagent.entity.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {

    List<KnowledgeChunk> findByClientIdOrderByIndexedAtDesc(String clientId);

    boolean existsBySourceUrl(String sourceUrl);

    void deleteByClientId(String clientId);
}
