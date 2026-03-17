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

    private Mac mac;

    @PostConstruct
    public void init() throws NoSuchAlgorithmException, InvalidKeyException {
        mac = Mac.getInstance(ALGORITHM);
        mac.init(new SecretKeySpec(secretKeyStr.getBytes(StandardCharsets.UTF_8), ALGORITHM));
    }

    /**
     * 웹훅 서명을 검증한다.
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
            // Mac 인스턴스는 상태를 가지므로 synchronized로 보호
            byte[] expected;
            synchronized (mac) {
                expected = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            }
            byte[] actual = Base64.getDecoder().decode(signature);

            if (!MessageDigest.isEqual(expected, actual)) {
                log.warn("웹훅 서명 불일치");
                throw new BusinessException(ErrorCode.WEBHOOK_SIGNATURE_INVALID);
            }
        } catch (IllegalArgumentException e) {
            // Base64 디코딩 실패
            log.warn("웹훅 서명 Base64 디코딩 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }
    }
}
