package com.stockmanagement.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.stockmanagement.common.exception.ErrorCode;
import lombok.Getter;

import java.util.List;

/**
 * 모든 API 응답을 감싸는 통합 응답 래퍼.
 *
 * <p>성공 시: {@code { "success": true, "data": { ... } }}
 * <br>실패 시: {@code { "success": false, "errorCode": "ORDER_NOT_FOUND", "message": "오류 메시지" }}
 * <br>검증 실패 시: {@code { "success": false, "errors": [{ "field": "email", "message": "..." }] }}
 *
 * <p>null 필드는 JSON 직렬화에서 제외된다 ({@link JsonInclude#NON_NULL}).
 * 생성자를 private으로 막고 정적 팩토리 메서드만 노출해 일관된 형태를 강제한다.
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final String message;

    /** 비즈니스 예외 에러 코드 — null이면 JSON 생략 */
    private final String errorCode;

    /** @Valid 필드별 에러 목록 — null이면 JSON 생략 */
    private final List<FieldErrorDetail> errors;

    private ApiResponse(boolean success, T data, String message,
                        String errorCode, List<FieldErrorDetail> errors) {
        this.success = success;
        this.data = data;
        this.message = message;
        this.errorCode = errorCode;
        this.errors = errors;
    }

    /** 데이터가 있는 성공 응답 */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null, null);
    }

    /** 데이터 없는 성공 응답 (204 No Content 등) */
    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(true, null, null, null, null);
    }

    /** 실패 응답 — errorCode 없이 message만 포함 (레거시 호환) */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message, null, null);
    }

    /** 실패 응답 — ErrorCode + message 포함 */
    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, null, message, errorCode.name(), null);
    }

    /** @Valid 검증 실패 응답 — 필드별 에러 목록 포함 */
    public static <T> ApiResponse<T> validationError(List<FieldErrorDetail> errors) {
        return new ApiResponse<>(false, null, "입력값이 올바르지 않습니다.", null, errors);
    }

    /** 필드별 에러 정보. */
    public record FieldErrorDetail(String field, String message) {}
}
