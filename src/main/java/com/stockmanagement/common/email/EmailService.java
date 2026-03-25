package com.stockmanagement.common.email;

import com.stockmanagement.domain.order.entity.OrderItem;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * 이메일 발송 서비스.
 *
 * <p>{@code mail.enabled=true} 환경에서만 빈이 생성된다.
 * SMTP 설정({@code spring.mail.*})이 함께 필요하다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mail.enabled", havingValue = "true")
public class EmailService {

    private final JavaMailSender mailSender;
    private final RetryRegistry retryRegistry;

    @Value("${mail.from:noreply@stockmall.com}")
    private String from;

    // ══ 공개 API ══

    public void sendOrderCreated(String to, Long orderId, BigDecimal totalAmount, List<OrderItem> items) {
        String content = """
                <h2 style="margin:0 0 6px;font-size:18px;color:#111;">주문이 접수되었습니다 🛍️</h2>
                <p style="margin:0 0 20px;color:#666;font-size:14px;">주문해 주셔서 감사합니다. 아래 내용을 확인해 주세요.</p>
                %s
                %s
                <p style="font-size:13px;color:#555;margin-top:20px;">결제가 완료되면 배송이 준비됩니다.</p>
                """.formatted(
                infoBox(
                        row("주문번호", "#" + orderId),
                        row("결제금액", formatPrice(totalAmount))
                ),
                buildItemsTable(items)
        );
        send(to, "[StockMall] 주문이 접수되었습니다 (#" + orderId + ")", wrap(content));
    }

    public void sendOrderCancelled(String to, Long orderId, String reason) {
        String reasonLabel = "PAYMENT_REFUNDED".equals(reason) ? "결제 취소(환불)" : "주문 취소";
        String content = """
                <h2 style="margin:0 0 6px;font-size:18px;color:#111;">주문이 취소되었습니다</h2>
                <p style="margin:0 0 20px;color:#666;font-size:14px;">아래 주문이 취소 처리되었습니다.</p>
                %s
                <p style="font-size:13px;color:#555;margin-top:20px;">
                  환불이 발생한 경우 영업일 기준 3~5일 내에 처리됩니다.
                </p>
                """.formatted(infoBox(
                row("주문번호", "#" + orderId),
                row("취소 사유", reasonLabel)
        ));
        send(to, "[StockMall] 주문이 취소되었습니다 (#" + orderId + ")", wrap(content));
    }

    public void sendPaymentConfirmed(String to, Long orderId, BigDecimal amount) {
        String content = """
                <h2 style="margin:0 0 6px;font-size:18px;color:#111;">결제가 완료되었습니다 ✅</h2>
                <p style="margin:0 0 20px;color:#666;font-size:14px;">결제가 정상적으로 처리되었습니다.</p>
                %s
                <p style="font-size:13px;color:#555;margin-top:20px;">상품 준비 후 배송이 시작되며 출고 시 별도 안내해 드립니다.</p>
                """.formatted(infoBox(
                row("주문번호", "#" + orderId),
                row("결제금액", formatPrice(amount))
        ));
        send(to, "[StockMall] 결제가 완료되었습니다 (#" + orderId + ")", wrap(content));
    }

    public void sendShipmentShipped(String to, Long orderId, String carrier, String trackingNumber) {
        String content = """
                <h2 style="margin:0 0 6px;font-size:18px;color:#111;">상품이 출고되었습니다 🚚</h2>
                <p style="margin:0 0 20px;color:#666;font-size:14px;">주문하신 상품이 출발했습니다!</p>
                %s
                <p style="font-size:13px;color:#555;margin-top:20px;">
                  택배사 앱 또는 홈페이지에서 운송장 번호로 배송 현황을 조회할 수 있습니다.
                </p>
                """.formatted(infoBox(
                row("주문번호", "#" + orderId),
                row("택배사", carrier),
                row("운송장 번호", trackingNumber)
        ));
        send(to, "[StockMall] 상품이 출고되었습니다 (#" + orderId + ")", wrap(content));
    }

    public void sendShipmentDelivered(String to, Long orderId) {
        String content = """
                <h2 style="margin:0 0 6px;font-size:18px;color:#111;">배송이 완료되었습니다 📦</h2>
                <p style="margin:0 0 20px;color:#666;font-size:14px;">주문하신 상품이 도착했습니다. 잘 받아보셨나요?</p>
                %s
                <p style="font-size:13px;color:#555;margin-top:20px;">
                  상품에 문제가 있다면 고객센터로 문의해 주세요.
                </p>
                """.formatted(infoBox(
                row("주문번호", "#" + orderId)
        ));
        send(to, "[StockMall] 배송이 완료되었습니다 (#" + orderId + ")", wrap(content));
    }

    // ══ 내부 헬퍼 ══

    /**
     * Retry 적용 send — 최대 3회 재시도 (2초 간격).
     * 최종 실패 시 예외를 삼키고 에러 로그만 남긴다(원본 트랜잭션에 영향 없음).
     */
    private void send(String to, String subject, String html) {
        Retry retry = retryRegistry.retry("email");
        try {
            Retry.decorateCheckedRunnable(retry, () -> doSend(to, subject, html)).run();
        } catch (Throwable t) {
            log.error("[Mail] 최종 전송 실패 (재시도 소진) to={} subject={} error={}", to, subject, t.getMessage());
        }
    }

    private void doSend(String to, String subject, String html) throws MessagingException {
        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);
        mailSender.send(msg);
        log.info("[Mail] 전송 완료 to={} subject={}", to, subject);
    }

    /** 공통 HTML 래퍼 (헤더 + 본문 + 푸터) */
    private String wrap(String content) {
        return """
                <!DOCTYPE html>
                <html lang="ko">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
                <body style="margin:0;padding:0;background:#f4f6f8;font-family:-apple-system,'Malgun Gothic',Arial,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f6f8;padding:32px 0;">
                    <tr><td align="center">
                      <table width="560" cellpadding="0" cellspacing="0"
                             style="background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.08);">
                        <!-- 헤더 -->
                        <tr>
                          <td style="background:#FF470A;padding:20px 28px;">
                            <span style="font-size:20px;font-weight:900;color:#fff;letter-spacing:-.5px;">
                              Stock<span style="opacity:.8">Mall</span>
                            </span>
                          </td>
                        </tr>
                        <!-- 본문 -->
                        <tr>
                          <td style="padding:28px 28px 24px;">
                            %s
                          </td>
                        </tr>
                        <!-- 푸터 -->
                        <tr>
                          <td style="background:#f8f9fa;border-top:1px solid #eee;padding:14px 28px;">
                            <p style="margin:0;font-size:11px;color:#aaa;">
                              본 메일은 발신 전용입니다. 문의: support@stockmall.com &nbsp;|&nbsp; © 2026 StockMall
                            </p>
                          </td>
                        </tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(content);
    }

    /** 정보 박스 (여러 row를 묶는 회색 박스) */
    private String infoBox(String... rows) {
        String rowsHtml = String.join("", rows);
        return """
                <table width="100%%" cellpadding="0" cellspacing="0"
                       style="background:#f8f9fa;border-radius:6px;padding:4px 0;margin-bottom:4px;">
                  %s
                </table>
                """.formatted(rowsHtml);
    }

    /** 정보 박스 내 항목 한 줄 — label/value를 HTML 이스케이프하여 XSS 방지 */
    private String row(String label, String value) {
        return """
                <tr>
                  <td style="padding:9px 16px;font-size:13px;color:#888;width:120px;">%s</td>
                  <td style="padding:9px 16px;font-size:13px;color:#111;font-weight:600;">%s</td>
                </tr>
                """.formatted(HtmlUtils.htmlEscape(label), HtmlUtils.htmlEscape(value));
    }

    /** 주문 항목 목록 테이블 (없으면 빈 문자열) */
    private String buildItemsTable(List<OrderItem> items) {
        if (items == null || items.isEmpty()) return "";
        StringBuilder rows = new StringBuilder();
        for (OrderItem item : items) {
            rows.append(row(
                    item.getProduct().getName() + " × " + item.getQuantity(),
                    formatPrice(item.getSubtotal())
            ));
        }
        return """
                <p style="margin:16px 0 6px;font-size:13px;font-weight:600;color:#333;">주문 상품</p>
                """ + infoBox(rows.toString());
    }

    private String formatPrice(BigDecimal amount) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(amount) + "원";
    }
}
