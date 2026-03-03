package com.stockmanagement.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 모든 API 응답을 감싸는 통합 응답 래퍼.
 *
 * <p>성공 시: {@code { "success": true, "data": { ... } }}
 * <br>실패 시: {@code { "success": false, "message": "오류 메시지" }}
 *
 * <p>null 필드는 JSON 직렬화에서 제외된다 ({@link JsonInclude#NON_NULL}).
 * 생성자를 private으로 막고 정적 팩토리 메서드만 노출해 일관된 형태를 강제한다.
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final String message;

    /** 데이터가 있는 성공 응답 */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /** 데이터 없는 성공 응답 (204 No Content 등) */
    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(true, null, null);
    }

    /** 실패 응답 — success=false, message에 오류 내용 */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message);
    }
}
