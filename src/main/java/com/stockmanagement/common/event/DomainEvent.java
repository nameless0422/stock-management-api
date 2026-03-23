package com.stockmanagement.common.event;

import java.time.LocalDateTime;

/** 모든 도메인 이벤트의 기반 클래스. */
public abstract class DomainEvent {

    private final LocalDateTime occurredAt;

    protected DomainEvent() {
        this.occurredAt = LocalDateTime.now();
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}
