package com.stockmanagement.common.outbox;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Transactional Outbox 이벤트 레코드.
 *
 * <p>비즈니스 트랜잭션과 같은 TX 안에서 저장되어, 서버 다운 후에도 이벤트 유실이 없다.
 * {@link OutboxEventRelayScheduler}가 주기적으로 미발행({@code publishedAt = NULL}) 레코드를
 * 읽어 Spring ApplicationEventPublisher로 발행한다.
 */
@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private OutboxEventType eventType;

    /** 이벤트 데이터 (JSON). */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** NULL이면 미발행. */
    @Column
    private LocalDateTime publishedAt;

    @Column(nullable = false)
    private int retryCount = 0;

    /** 최근 발행 실패 시각. */
    @Column
    private LocalDateTime failedAt;

    @Builder
    public OutboxEvent(OutboxEventType eventType, String payload) {
        this.eventType = eventType;
        this.payload = payload;
    }

    /** 발행 완료 표시. */
    public void markPublished() {
        this.publishedAt = LocalDateTime.now();
    }

    /** 발행 실패 기록 — retryCount 증가, failedAt 갱신. */
    public void recordFailure() {
        this.retryCount++;
        this.failedAt = LocalDateTime.now();
    }
}
