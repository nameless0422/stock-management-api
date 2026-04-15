package com.stockmanagement.domain.payment.infrastructure;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * TossPayments 웹훅 HMAC-SHA256 서명 검증.
 *
 * <p>TossPayments는 웹훅 요청 시 {@code Toss-Signature} 헤더에
 * {@code Base64(HMAC-SHA256(rawBody, secretKey))}를 포함해 전송한다.
 * Timing attack 방지를 위해 {@link MessageDigest#isEqual}로 constant-time 비교한다.
 */
@Slf4j
@Component
public class TossWebhookVerifier {

    private static final String ALGORITHM = "HmacSHA256";

    @Value("${toss.secret-key}")
    private String secretKeyStr;

    /** 서명 키 — 불변(immutable)이므로 thread-safe하게 공유 가능. */
    private SecretKeySpec secretKeySpec;

    @PostConstruct
    public void init() {
        // SecretKeySpec은 불변 객체 → 스레드 안전하게 재사용 가능
        this.secretKeySpec = new SecretKeySpec(
                secretKeyStr.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    /**
     * 웹훅 서명을 검증한다.
     *
     * <p>Mac 인스턴스는 호출마다 새로 생성한다. Mac은 thread-safe하지 않으므로
     * 싱글턴 공유 대신 per-call 생성 방식을 사용한다 (synchronized 병목 제거).
     * SecretKeySpec은 불변이므로 재사용한다.
     *
     * @param rawBody   요청 본문 원문 (String)
     * @param signature {@code Toss-Signature} 헤더 값 (Base64 인코딩)
     * @throws BusinessException 서명이 없거나 일치하지 않을 때
     */
    public void verify(String rawBody, String signature) {
        if (signature == null || signature.isBlank()) {
            log.warn("웹훅 서명 헤더 없음");
            throw new BusinessException(ErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }
        try {
            // Mac 인스턴스 per-call 생성 — synchronized 직렬화 병목 제거
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(secretKeySpec);
            byte[] expected = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            byte[] actual = Base64.getDecoder().decode(signature);

            if (!MessageDigest.isEqual(expected, actual)) {
                log.warn("웹훅 서명 불일치");
                throw new BusinessException(ErrorCode.WEBHOOK_SIGNATURE_INVALID);
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // HmacSHA256은 JDK 표준 — 실질적으로 발생하지 않음
            throw new IllegalStateException("HMAC 초기화 실패", e);
        } catch (IllegalArgumentException e) {
            // Base64 디코딩 실패
            log.warn("웹훅 서명 Base64 디코딩 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }
    }
}
