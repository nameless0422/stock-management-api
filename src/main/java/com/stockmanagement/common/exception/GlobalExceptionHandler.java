package com.stockmanagement.common.exception;

import com.stockmanagement.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 컨트롤러 레이어에서 발생하는 예외를 일괄 처리하는 전역 핸들러.
 *
 * <p>예외 종류별 우선순위:
 * <ol>
 *   <li>{@link BusinessException} — 도메인 규칙 위반 (4xx)
 *   <li>{@link MethodArgumentNotValidException} — Bean Validation 실패 (400)
 *   <li>{@link Exception} — 그 외 모든 예상치 못한 오류 (500)
 * </ol>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비즈니스 예외 처리.
     * ErrorCode에 정의된 HTTP 상태를 그대로 응답 코드로 사용한다.
     * warn 레벨로 로깅 (스택 트레이스 제외 — 예상된 예외이므로).
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("BusinessException: {}", e.getMessage());
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * @Valid 검증 실패 처리.
     * 여러 필드 오류를 쉼표로 합쳐 하나의 메시지로 반환한다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(message));
    }

    /**
     * 정적 리소스 없음 처리 — Spring MVC가 발생시키는 NoResourceFoundException을 404로 반환.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("요청한 리소스를 찾을 수 없습니다."));
    }

    /**
     * 예상치 못한 서버 오류 처리.
     * 내부 스택 트레이스는 로그에만 남기고, 클라이언트에는 일반 메시지만 반환한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity
                .internalServerError()
                .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }
}
