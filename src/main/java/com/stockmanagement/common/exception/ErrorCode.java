package com.stockmanagement.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 애플리케이션 전역 에러 코드.
 *
 * <p>각 코드는 HTTP 상태 코드와 사용자에게 노출할 메시지를 함께 보관한다.
 * {@link GlobalExceptionHandler}가 이 값을 읽어 응답 상태와 본문을 결정한다.
 *
 * <p>도메인별로 그룹을 나눠 관리하며, 새로운 도메인을 추가할 때 해당 그룹에
 * 코드를 추가한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ===== Product =====
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
    DUPLICATE_SKU(HttpStatus.CONFLICT, "이미 존재하는 SKU입니다."),

    // ===== Inventory =====
    INVENTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "재고 정보를 찾을 수 없습니다."),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다."),

    // ===== Order =====
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
    INVALID_ORDER_STATUS(HttpStatus.CONFLICT, "현재 주문 상태에서 허용되지 않는 작업입니다."),

    // ===== Payment =====
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제 정보를 찾을 수 없습니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "결제 금액이 일치하지 않습니다."),
    PAYMENT_ALREADY_PROCESSED(HttpStatus.CONFLICT, "이미 처리된 결제입니다."),
    INVALID_PAYMENT_STATUS(HttpStatus.CONFLICT, "현재 결제 상태에서 허용되지 않는 작업입니다."),
    TOSS_PAYMENTS_ERROR(HttpStatus.BAD_GATEWAY, "결제 처리 중 오류가 발생했습니다."),
    TOSS_PAYMENTS_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "결제 서비스가 일시적으로 불가합니다. 잠시 후 다시 시도해주세요."),
    PAYMENT_PROCESSING_IN_PROGRESS(HttpStatus.CONFLICT, "결제가 처리 중입니다. 잠시 후 다시 시도해주세요."),
    WEBHOOK_SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 웹훅 서명입니다."),

    // ===== User =====
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    DUPLICATE_USERNAME(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 Refresh Token입니다."),

    // ===== Common =====
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 입력값입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    LOCK_ACQUISITION_FAILED(HttpStatus.TOO_MANY_REQUESTS, "현재 요청이 많습니다. 잠시 후 다시 시도해주세요."),
    TOO_MANY_LOGIN_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "로그인 시도 횟수를 초과했습니다. 15분 후 다시 시도해주세요.");

    private final HttpStatus httpStatus;
    /** 클라이언트에 그대로 전달되는 메시지 — 민감 정보를 포함하지 않아야 한다. */
    private final String message;
}
