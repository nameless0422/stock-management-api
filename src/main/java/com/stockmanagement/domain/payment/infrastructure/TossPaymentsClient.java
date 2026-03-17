package com.stockmanagement.domain.payment.infrastructure;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.payment.infrastructure.dto.TossCancelRequest;
import com.stockmanagement.domain.payment.infrastructure.dto.TossConfirmRequest;
import com.stockmanagement.domain.payment.infrastructure.dto.TossConfirmResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
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
     * @param request confirmation payload (paymentKey, orderId, amount)
     * @return confirmation response with payment details
     * @throws BusinessException if TossPayments returns a 4xx or 5xx error
     */
    public TossConfirmResponse confirm(TossConfirmRequest request) {
        try {
            return tossRestClient.post()
                    .uri("/payments/confirm")
                    // Toss 측 중복 처리 방지: 동일 orderId 재요청 시 이전 결과 반환
                    .header("Idempotency-Key", request.getOrderId())
                    .body(request)
                    .retrieve()
                    .body(TossConfirmResponse.class);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("TossPayments confirm API error: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.TOSS_PAYMENTS_ERROR,
                    "TossPayments confirm failed: " + e.getStatusCode());
        }
    }

    /**
     * Calls TossPayments payment cancellation API.
     * POST /payments/{paymentKey}/cancel
     *
     * @param paymentKey TossPayments-assigned payment key to cancel
     * @param request    cancellation payload (reason and optional partial amount)
     * @return updated payment details after cancellation
     * @throws BusinessException if TossPayments returns a 4xx or 5xx error
     */
    public TossConfirmResponse cancel(String paymentKey, TossCancelRequest request) {
        try {
            return tossRestClient.post()
                    .uri("/payments/{paymentKey}/cancel", paymentKey)
                    .body(request)
                    .retrieve()
                    .body(TossConfirmResponse.class);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("TossPayments cancel API error: paymentKey={}, status={}, body={}",
                    paymentKey, e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.TOSS_PAYMENTS_ERROR,
                    "TossPayments cancel failed: " + e.getStatusCode());
        }
    }
}
