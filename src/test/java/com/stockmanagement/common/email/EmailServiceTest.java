package com.stockmanagement.common.email;

import com.stockmanagement.domain.order.entity.OrderItem;
import com.stockmanagement.domain.product.entity.Product;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

@DisplayName("EmailService 단위 테스트")
class EmailServiceTest {

    private JavaMailSender mailSender;
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        // 테스트용 Retry: 3회 재시도, 대기 없음
        RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ZERO)
                .retryExceptions(Exception.class)
                .build());
        emailService = new EmailService(mailSender, retryRegistry);
        ReflectionTestUtils.setField(emailService, "from", "noreply@test.com");

        MimeMessage mimeMessage = mock(MimeMessage.class);
        given(mailSender.createMimeMessage()).willReturn(mimeMessage);
    }

    @Nested
    @DisplayName("sendOrderCreated")
    class SendOrderCreated {

        @Test
        @DisplayName("items 없이 호출 → mailSender.send() 1회 실행")
        void sendsWithoutItems() {
            emailService.sendOrderCreated("user@test.com", 1L, BigDecimal.valueOf(10000), List.of());

            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("items 포함 호출 → mailSender.send() 1회 실행")
        void sendsWithItems() {
            OrderItem item = mockOrderItem("테스트 상품", 2, BigDecimal.valueOf(4000));

            emailService.sendOrderCreated("user@test.com", 1L, BigDecimal.valueOf(4000), List.of(item));

            verify(mailSender).send(any(MimeMessage.class));
        }
    }

    @Nested
    @DisplayName("기타 이메일 발송")
    class OtherEmails {

        @Test
        @DisplayName("sendOrderCancelled → mailSender.send() 1회 실행")
        void sendsCancelled() {
            emailService.sendOrderCancelled("user@test.com", 1L, "주문 취소");

            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("sendPaymentConfirmed → mailSender.send() 1회 실행")
        void sendsPaymentConfirmed() {
            emailService.sendPaymentConfirmed("user@test.com", 1L, BigDecimal.valueOf(5000));

            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("sendShipmentShipped → mailSender.send() 1회 실행")
        void sendsShipped() {
            emailService.sendShipmentShipped("user@test.com", 1L, "한진택배", "123456789");

            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("sendShipmentDelivered → mailSender.send() 1회 실행")
        void sendsDelivered() {
            emailService.sendShipmentDelivered("user@test.com", 1L);

            verify(mailSender).send(any(MimeMessage.class));
        }
    }

    @Nested
    @DisplayName("Retry 메커니즘")
    class RetryBehavior {

        @Test
        @DisplayName("SMTP 실패 시 3회 재시도 후 예외 전파 없음")
        void retriesOnSmtpFailureAndSwallowsException() {
            willThrow(new MailSendException("SMTP 오류")).given(mailSender).send(any(MimeMessage.class));

            assertThatNoException().isThrownBy(() ->
                    emailService.sendOrderCancelled("user@test.com", 1L, "취소")
            );
            // max-attempts=3 → createMimeMessage 3회 호출
            verify(mailSender, times(3)).createMimeMessage();
        }

        @Test
        @DisplayName("첫 번째 실패 후 재시도에서 성공 → send() 총 2회 호출")
        void succeedsOnSecondAttempt() {
            willThrow(new MailSendException("일시 장애"))
                    .willDoNothing()
                    .given(mailSender).send(any(MimeMessage.class));

            assertThatNoException().isThrownBy(() ->
                    emailService.sendShipmentDelivered("user@test.com", 1L)
            );
            verify(mailSender, times(2)).send(any(MimeMessage.class));
        }
    }

    // ── 헬퍼 ──

    private OrderItem mockOrderItem(String name, int quantity, BigDecimal subtotal) {
        Product product = mock(Product.class);
        given(product.getName()).willReturn(name);
        OrderItem item = mock(OrderItem.class);
        given(item.getProduct()).willReturn(product);
        given(item.getQuantity()).willReturn(quantity);
        given(item.getSubtotal()).willReturn(subtotal);
        return item;
    }
}
