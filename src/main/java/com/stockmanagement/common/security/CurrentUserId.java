package com.stockmanagement.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 현재 인증된 사용자의 userId를 컨트롤러 메서드 파라미터에 주입한다.
 *
 * <p>JWT claims의 userId를 우선 사용하고, 없으면 DB fallback으로 조회한다.
 * {@code required = false}이면 미인증 시 null을 반환한다.
 *
 * <pre>
 * {@literal @}GetMapping
 * public ApiResponse&lt;CartResponse&gt; getCart(@CurrentUserId Long userId) { ... }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserId {

    /** false이면 미인증 요청에서 null 반환 (기본: 인증 필수) */
    boolean required() default true;
}
