package com.stockmanagement.common.event;

import com.stockmanagement.common.outbox.OutboxEventType;

import java.util.Map;

/**
 * Transactional Outbox에 저장할 수 있는 이벤트가 구현하는 인터페이스.
 *
 * <p>이벤트 클래스가 자신의 Outbox 타입과 페이로드를 직접 정의하므로,
 * {@link com.stockmanagement.common.outbox.OutboxEventStore}는 이벤트 타입별 분기 없이
 * 단일 {@code save(OutboxSupport)} 메서드로 모든 이벤트를 처리한다.
 *
 * <p>새 이벤트를 Outbox로 발행하려면 이 인터페이스를 구현하고
 * {@link com.stockmanagement.common.outbox.OutboxEventProcessor}의 {@code buildEvent()} 스위치에
 * case를 추가한다.
 */
public interface OutboxSupport {

    /** 이 이벤트에 해당하는 Outbox 이벤트 타입. */
    OutboxEventType outboxEventType();

    /** Outbox 테이블에 직렬화할 페이로드 (JSON으로 변환됨). */
    Map<String, Object> toOutboxPayload();
}
