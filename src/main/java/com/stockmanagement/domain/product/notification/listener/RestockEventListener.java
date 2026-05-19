package com.stockmanagement.domain.product.notification.listener;

import com.stockmanagement.common.event.RestockEvent;
import com.stockmanagement.domain.product.notification.service.RestockNotificationService;
import com.stockmanagement.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Optional;

/**
 * 재입고 이벤트 리스너 — 알림 구독자에게 이메일 발송 후 구독 해제.
 *
 * <p>{@link com.stockmanagement.common.email.EmailService}는 {@code mail.enabled=true}에서만
 * 빈이 생성되므로 Optional 주입으로 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestockEventListener {

    private final RestockNotificationService restockNotificationService;
    private final UserRepository userRepository;
    private final Optional<com.stockmanagement.common.email.EmailService> emailService;

    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRestock(RestockEvent event) {
        // 구독자 조회 + 구독 삭제 (트랜잭션은 서비스에서 관리)
        List<Long> userIds = restockNotificationService.consumeSubscribers(event.getProductId());
        if (userIds.isEmpty()) {
            return;
        }

        log.info("[Restock] 재입고 알림 발송 시작: productId={}, subscribers={}", event.getProductId(), userIds.size());

        emailService.ifPresent(service ->
                userRepository.findAllById(userIds).forEach(user ->
                        service.sendRestockAvailable(user.getEmail(), event.getProductName())
                )
        );

        log.info("[Restock] 재입고 알림 발송 완료: productId={}, notified={}", event.getProductId(), userIds.size());
    }
}
