package com.stockmanagement.domain.payment.infrastructure;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.payment.infrastructure.dto.TossCancelRequest;
import com.stockmanagement.domain.payment.infrastructure.dto.TossConfirmRequest;
import com.stockmanagement.domain.payment.infrastructure.dto.TossConfirmResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * HTTP client for TossPayments Core API.
 *
 * <p>Wraps Spring's {@link RestClient} (pre-configured in {@code TossPaymentsConfig})
 * with TossPayments-specific error handling. HTTP 4xx and 5xx responses from Toss
 * are caught and re-thrown as {@link BusinessException} with {@link ErrorCode#TOSS_PAYMENTS_ERROR}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TossPaymentsClient {

    private final RestClient tossRestClient;

    /**
     * Calls TossPayments payment confirmation API.
     * POST /payments/confirm
     *
     * <p>4xx 오류(잘못된 요청 등)는 {@link BusinessException}으로 변환 후 재전파한다.
     * ignore-exceptions 설정에 따라 Circuit Breaker가 4xx를 실패로 기록하지 않는다.
     * 5xx·네트워크 오류는 CB 실패로 기록되며 회로가 열리면 {@link #confirmFallback}을 호출한다.
     */
    @CircuitBreaker(name = "tossPayments", fallbackMethod = "confirmFallback")
    public TossConfirmResponse confirm(TossConfirmRequest request) {
        try {
            return tossRestClient.post()
                    .uri("/payments/confirm")
                    // Toss 측 중복 처리 방지: 동일 orderId 재요청 시 이전 결과 반환
                    .header("Idempotency-Key", request.getOrderId())
                    .body(request)
                    .retrieve()
                    .body(TossConfirmResponse.class);
        } catch (HttpClientErrorException e) {
            // 4xx: 결제 요청 자체의 비즈니스 오류 → CB 실패로 집계 안 함
            log.error("TossPayments confirm 4xx error: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.TOSS_PAYMENTS_ERROR,
                    "TossPayments confirm failed: " + e.getStatusCode());
        }
        // 5xx·네트워크 오류는 CB가 실패로 기록 → 임계치 초과 시 confirmFallback 호출
    }

    /**
     * Calls TossPayments payment cancellation API.
     * POST /payments/{paymentKey}/cancel
     *
     * <p>4xx 오류는 {@link BusinessException}으로 변환, 5xx·네트워크 오류는 CB에 위임한다.
     */
    @CircuitBreaker(name = "tossPayments", fallbackMethod = "cancelFallback")
    public TossConfirmResponse cancel(String paymentKey, TossCancelRequest request) {
        try {
            return tossRestClient.post()
                    .uri("/payments/{paymentKey}/cancel", paymentKey)
                    .body(request)
                    .retrieve()
                    .body(TossConfirmResponse.class);
        } catch (HttpClientErrorException e) {
            // 4xx: 취소 요청 자체의 비즈니스 오류 → CB 실패로 집계 안 함
            log.error("TossPayments cancel 4xx error: paymentKey={}, status={}, body={}",
                    paymentKey, e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.TOSS_PAYMENTS_ERROR,
                    "TossPayments cancel failed: " + e.getStatusCode());
        }
        // 5xx·네트워크 오류는 CB가 실패로 기록 → 임계치 초과 시 cancelFallback 호출
    }

    // ===== Circuit Breaker fallback =====

    /** confirm() 호출 불가(CB OPEN 또는 5xx·타임아웃) 시 서비스 불가 예외 발생. */
    private TossConfirmResponse confirmFallback(TossConfirmRequest request, Exception e) {
        log.error("TossPayments confirm fallback triggered: {}", e.getMessage());
        throw new BusinessException(ErrorCode.TOSS_PAYMENTS_UNAVAILABLE);
    }

    /** cancel() 호출 불가(CB OPEN 또는 5xx·타임아웃) 시 서비스 불가 예외 발생. */
    private TossConfirmResponse cancelFallback(String paymentKey, TossCancelRequest request, Exception e) {
        log.error("TossPayments cancel fallback triggered: paymentKey={}, cause={}", paymentKey, e.getMessage());
        throw new BusinessException(ErrorCode.TOSS_PAYMENTS_UNAVAILABLE);
    }
}
