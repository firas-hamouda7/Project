package com.example.aiagent.repository;

import com.example.aiagent.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findAllByOrderByCreatedAtDesc();
    List<Notification> findByIsReadFalseOrderByCreatedAtDesc();
}
