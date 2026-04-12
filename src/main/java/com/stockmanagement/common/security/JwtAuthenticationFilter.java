package com.stockmanagement.common.security;

import com.stockmanagement.common.security.JwtBlacklist;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtBlacklist jwtBlacklist;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)
                && !jwtBlacklist.isRevoked(token)) {
            String username = jwtTokenProvider.getUsername(token);
            String role = jwtTokenProvider.getRole(token);

            var authority = new SimpleGrantedAuthority("ROLE_" + role);
            var authentication = new UsernamePasswordAuthenticationToken(
                    username, null, List.of(authority));

            // userId 클레임이 존재하면 details에 저장하여 서비스 레이어 DB 조회를 제거한다
            Long userId = jwtTokenProvider.getUserId(token);
            if (userId != null) {
                authentication.setDetails(userId);
            }

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    // Authorization: Bearer <token> 헤더에서 토큰 추출
    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
