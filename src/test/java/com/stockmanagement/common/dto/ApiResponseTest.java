package com.stockmanagement.common.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockmanagement.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiResponse 단위 테스트")
class ApiResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("ok() — success=true, data 포함, errorCode/errors null")
    void ok_withData() throws Exception {
        ApiResponse<String> response = ApiResponse.ok("hello");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo("hello");
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
        assertThat(response.getErrors()).isNull();

        String json = objectMapper.writeValueAsString(response);
        assertThat(json).contains("\"success\":true");
        assertThat(json).contains("\"data\":\"hello\"");
        assertThat(json).doesNotContain("errorCode");
        assertThat(json).doesNotContain("errors");
    }

    @Test
    @DisplayName("error(String) — errorCode 없이 message만 포함 (레거시 호환)")
    void error_withMessageOnly() throws Exception {
        ApiResponse<Void> response = ApiResponse.error("오류 발생");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).isEqualTo("오류 발생");
        assertThat(response.getErrorCode()).isNull();

        String json = objectMapper.writeValueAsString(response);
        assertThat(json).contains("\"success\":false");
        assertThat(json).contains("\"message\":\"오류 발생\"");
        assertThat(json).doesNotContain("errorCode");
    }

    @Test
    @DisplayName("error(ErrorCode, String) — errorCode 포함")
    void error_withErrorCode() throws Exception {
        ApiResponse<Void> response = ApiResponse.error(ErrorCode.ORDER_NOT_FOUND, "주문을 찾을 수 없습니다.");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo("ORDER_NOT_FOUND");
        assertThat(response.getMessage()).isEqualTo("주문을 찾을 수 없습니다.");
        assertThat(response.getErrors()).isNull();

        String json = objectMapper.writeValueAsString(response);
        assertThat(json).contains("\"errorCode\":\"ORDER_NOT_FOUND\"");
        assertThat(json).contains("\"message\":\"주문을 찾을 수 없습니다.\"");
        assertThat(json).doesNotContain("\"errors\"");
    }

    @Test
    @DisplayName("validationError() — errors 필드에 필드별 에러 포함")
    void validationError_withFieldErrors() throws Exception {
        List<ApiResponse.FieldErrorDetail> errors = List.of(
                new ApiResponse.FieldErrorDetail("email", "이메일 형식이 올바르지 않습니다."),
                new ApiResponse.FieldErrorDetail("password", "비밀번호는 8자 이상이어야 합니다.")
        );
        ApiResponse<Void> response = ApiResponse.validationError(errors);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrors()).hasSize(2);
        assertThat(response.getErrors().get(0).field()).isEqualTo("email");
        assertThat(response.getErrors().get(1).field()).isEqualTo("password");
        assertThat(response.getErrorCode()).isNull();

        String json = objectMapper.writeValueAsString(response);
        assertThat(json).contains("\"errors\"");
        assertThat(json).contains("\"field\":\"email\"");
        assertThat(json).doesNotContain("\"errorCode\"");
    }

    @Test
    @DisplayName("UNAUTHORIZED/FORBIDDEN ErrorCode — errorCode 이름 검증")
    void securityErrorCodes() {
        ApiResponse<Void> unauthorized = ApiResponse.error(ErrorCode.UNAUTHORIZED, "인증이 필요합니다.");
        ApiResponse<Void> forbidden = ApiResponse.error(ErrorCode.FORBIDDEN, "접근 권한이 없습니다.");

        assertThat(unauthorized.getErrorCode()).isEqualTo("UNAUTHORIZED");
        assertThat(forbidden.getErrorCode()).isEqualTo("FORBIDDEN");
    }
}
