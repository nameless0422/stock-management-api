package com.stockmanagement.domain.notification.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 인앱 알림 엔티티.
 *
 * <p>주문/결제/배송 등 주요 이벤트 발생 시 사용자에게 전달되는 알림을 저장한다.
 * readAt이 null이면 미읽음 상태.
 */
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private NotificationType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Notification(Long userId, NotificationType type, String title, String message) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.message = message;
    }

    /** 읽음 처리 */
    public void markAsRead() {
        if (this.readAt == null) {
            this.readAt = LocalDateTime.now();
        }
    }

    public boolean isRead() {
        return readAt != null;
    }
}
