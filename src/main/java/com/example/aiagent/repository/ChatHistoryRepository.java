package com.example.aiagent.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.example.aiagent.entity.ChatHistory;

@Repository
public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {

    Optional<ChatHistory> findFirstByOrderByIdDesc();

    Page<ChatHistory> findByClientEmailOrderByTimestampDesc(String clientEmail, Pageable pageable);
}
