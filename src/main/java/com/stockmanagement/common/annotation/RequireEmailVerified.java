package com.stockmanagement.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이메일 인증이 완료된 사용자만 접근 가능한 메서드에 표시한다.
 * {@link com.stockmanagement.common.aspect.EmailVerifiedAspect}가 AOP로 검증한다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireEmailVerified {
}
