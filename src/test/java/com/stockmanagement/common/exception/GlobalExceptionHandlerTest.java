package com.stockmanagement.common.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GlobalExceptionHandler 동작 검증 테스트.
 *
 * <p>standaloneSetup 방식으로 Spring 컨텍스트 없이 테스트.
 * BusinessException errorCode 포함 여부, @Valid 필드별 errors 구조를 검증한다.
 */
@DisplayName("GlobalExceptionHandler 단위 테스트")
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ===== 테스트용 컨트롤러 및 DTO =====

    @RestController
    static class TestController {

        @GetMapping("/test/business-error")
        public void throwBusiness() {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        @PostMapping("/test/validation")
        public void withValidation(@RequestBody @Valid TestRequest request) {}
    }

    record TestRequest(
            @NotBlank(message = "이름은 필수입니다.") String name,
            @Email(message = "이메일 형식이 올바르지 않습니다.") @NotBlank(message = "이메일은 필수입니다.") String email
    ) {}

    // ===== 테스트 =====

    @Test
    @DisplayName("BusinessException → errorCode 포함 응답")
    void businessException_includesErrorCode() throws Exception {
        mockMvc.perform(get("/test/business-error"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("@Valid 실패 → errors 배열에 필드별 에러 포함")
    void validationError_returnsFieldErrors() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItems("name", "email")))
                .andExpect(jsonPath("$.errors[*].message", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.errorCode").doesNotExist());
    }

    @Test
    @DisplayName("@Valid 실패 → message는 단일 문자열, errorCode는 없음")
    void validationError_noErrorCode() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors.length()", greaterThan(0)))
                .andExpect(jsonPath("$.errorCode").doesNotExist());
    }
}
