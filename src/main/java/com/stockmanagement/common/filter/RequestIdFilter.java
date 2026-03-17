package com.stockmanagement.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 모든 요청에 고유한 requestId를 MDC에 주입하는 필터.
 *
 * <p>X-Request-Id 요청 헤더가 있으면 그대로 사용하고, 없으면 UUID를 생성한다.
 * 응답 헤더 X-Request-Id에도 동일한 값을 추가하여 클라이언트가 추적할 수 있게 한다.
 */
@Component
@Order(Integer.MIN_VALUE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_KEY    = "requestId";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(REQUEST_ID_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(REQUEST_ID_KEY);
        }
    }
}
