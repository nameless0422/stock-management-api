package com.stockmanagement.domain.user.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.domain.order.dto.OrderResponse;
import com.stockmanagement.domain.user.dto.UserResponse;
import com.stockmanagement.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 REST API 컨트롤러.
 *
 * <pre>
 * GET /api/users/me          내 정보 조회 → 200 OK
 * GET /api/users/me/orders   내 주문 목록 (페이징) → 200 OK
 * </pre>
 *
 * JWT 인증이 필요하다. {@link AuthenticationPrincipal}로 username을 주입받아 서비스에 전달한다.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<UserResponse> getMe(@AuthenticationPrincipal String username) {
        return ApiResponse.ok(userService.getMe(username));
    }

    @GetMapping("/me/orders")
    public ApiResponse<Page<OrderResponse>> getMyOrders(
            @AuthenticationPrincipal String username,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.ok(userService.getMyOrders(username, pageable));
    }
}
