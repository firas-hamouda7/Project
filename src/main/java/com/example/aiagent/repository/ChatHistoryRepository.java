package com.example.aiagent.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.example.aiagent.entity.ChatHistory;

@Repository
public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {
    
    // On trie par ID (décroissant) pour être 100% sûr d'avoir le tout dernier message créé
    Optional<ChatHistory> findFirstByOrderByIdDesc();
}
