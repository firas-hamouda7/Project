package com.example.aiagent.repository;

import com.example.aiagent.entity.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {

    List<KnowledgeChunk> findByClientIdOrderByIndexedAtDesc(String clientId);

    boolean existsBySourceUrl(String sourceUrl);

    void deleteByClientId(String clientId);
}
