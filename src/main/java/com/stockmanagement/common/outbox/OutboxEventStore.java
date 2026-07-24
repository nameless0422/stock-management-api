package com.stockmanagement.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockmanagement.common.event.OutboxSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 도메인 이벤트를 Outbox 테이블에 저장하는 서비스.
 *
 * <p>호출 측의 트랜잭션에 참여하여({@link Propagation#MANDATORY}) 비즈니스 로직과
 * 이벤트 저장을 하나의 원자적 트랜잭션으로 처리한다.
 *
 * <p>이벤트 타입별 분기가 없으며, {@link OutboxSupport}를 구현한 이벤트라면
 * 모두 동일한 방식으로 저장된다. 새 이벤트 타입을 추가할 때 이 클래스를 수정할 필요가 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class OutboxEventStore {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * {@link OutboxSupport}를 구현한 도메인 이벤트를 Outbox 테이블에 저장한다.
     *
     * @param event Outbox 저장을 지원하는 도메인 이벤트
     */
    public void save(OutboxSupport event) {
        repository.save(toEntity(event));
    }

    /**
     * 독립 트랜잭션(REQUIRES_NEW)으로 Outbox 이벤트를 저장한다.
     *
     * <p>AFTER_COMMIT 리스너처럼 이미 커밋된 트랜잭션 컨텍스트에서 호출할 때 사용한다.
     * 그 컨텍스트에서 {@link #save}로 저장하면 커밋이 끝난 트랜잭션에 참여하여
     * INSERT가 유실되므로 반드시 새 트랜잭션으로 커밋해야 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveInNewTransaction(OutboxSupport event) {
        repository.save(toEntity(event));
    }

    private OutboxEvent toEntity(OutboxSupport event) {
        try {
            return OutboxEvent.builder()
                    .eventType(event.outboxEventType())
                    .payload(objectMapper.writeValueAsString(event.toOutboxPayload()))
                    .build();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Outbox 이벤트 직렬화 실패: " + event.outboxEventType(), ex);
        }
    }
}
