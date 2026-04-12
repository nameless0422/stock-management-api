package com.stockmanagement.common.exception;

import lombok.Getter;

/**
 * 비즈니스 규칙 위반 시 발생하는 최상위 커스텀 예외.
 *
 * <p>모든 도메인별 예외는 이 클래스를 상속하거나 직접 이 클래스를 사용한다.
 * {@link GlobalExceptionHandler}가 이 타입을 잡아 {@link ErrorCode}에 정의된
 * HTTP 상태와 메시지로 변환한다.
 *
 * <p>사용 예:
 * <pre>{@code
 * throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
 * }</pre>
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * ErrorCode에 정의된 기본 메시지를 사용하는 생성자.
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 기본 메시지 대신 구체적인 메시지를 직접 지정하는 생성자.
     * ID 등 동적 값을 포함한 메시지가 필요할 때 사용한다.
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
