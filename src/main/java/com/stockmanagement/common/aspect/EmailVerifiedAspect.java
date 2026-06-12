package com.stockmanagement.common.aspect;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.user.entity.User;
import com.stockmanagement.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * {@code @RequireEmailVerified} 어노테이션이 달린 메서드 호출 전,
 * 현재 인증된 사용자의 이메일 인증 여부를 검증한다.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class EmailVerifiedAspect {

    private final UserRepository userRepository;

    @Before("@annotation(com.stockmanagement.common.annotation.RequireEmailVerified)")
    public void checkEmailVerified() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return; // Security 필터에서 처리
        }
        String username = auth.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!user.isEmailVerified()) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
    }
}
