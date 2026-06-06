package com.stockmanagement.domain.notification.repository;

import com.stockmanagement.domain.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadAtIsNotNullOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // 커서 기반 — 전체
    List<Notification> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);
    List<Notification> findByUserIdAndIdLessThanOrderByIdDesc(Long userId, Long lastId, Pageable pageable);

    // 커서 기반 — 미읽음
    List<Notification> findByUserIdAndReadAtIsNullOrderByIdDesc(Long userId, Pageable pageable);
    List<Notification> findByUserIdAndReadAtIsNullAndIdLessThanOrderByIdDesc(Long userId, Long lastId, Pageable pageable);

    // 커서 기반 — 읽음
    List<Notification> findByUserIdAndReadAtIsNotNullOrderByIdDesc(Long userId, Pageable pageable);
    List<Notification> findByUserIdAndReadAtIsNotNullAndIdLessThanOrderByIdDesc(Long userId, Long lastId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :readAt WHERE n.userId = :userId AND n.readAt IS NULL")
    int markAllAsRead(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);

    void deleteByUserId(Long userId);
}
