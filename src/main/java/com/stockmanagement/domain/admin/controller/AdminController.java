package com.stockmanagement.domain.admin.controller;

import com.stockmanagement.common.dto.ApiResponse;
import com.stockmanagement.domain.admin.dto.AdminOrderResponse;
import com.stockmanagement.domain.admin.dto.DashboardResponse;
import com.stockmanagement.domain.admin.dto.RoleUpdateRequest;
import com.stockmanagement.domain.admin.service.AdminService;
import com.stockmanagement.domain.inventory.dto.DailyInventorySnapshotResponse;
import com.stockmanagement.domain.order.dto.DailyOrderStatsResponse;
import com.stockmanagement.domain.order.entity.OrderStatus;
import com.stockmanagement.domain.product.dto.ProductResponse;
import com.stockmanagement.domain.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 관리자 전용 REST API 컨트롤러.
 *
 * <p>모든 엔드포인트는 ADMIN 권한이 필요하다 (SecurityConfig에서 일괄 설정).
 *
 * <pre>
 * GET   /api/admin/dashboard          대시보드 통계 → 200 OK
 * GET   /api/admin/users              전체 사용자 목록 (페이징) → 200 OK
 * PATCH /api/admin/users/{id}/role    사용자 권한 변경 → 200 OK
 * GET   /api/admin/orders             전체 주문 목록 (페이징, 상태 필터) → 200 OK
 * </pre>
 */
@Tag(name = "관리자", description = "ADMIN 전용 — 대시보드 · 사용자 관리 · 전체 주문 조회")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @Operation(summary = "관리자 대시보드", description = "주문 통계, 매출(CONFIRMED 기준), 사용자 수, 저재고(available<10) 목록 반환.")
    @GetMapping("/dashboard")
    public ApiResponse<DashboardResponse> getDashboard() {
        return ApiResponse.ok(adminService.getDashboard());
    }

    @Operation(summary = "전체 사용자 목록 (페이징)", description = "?search=키워드 로 username/email 검색. 기본: 가입일 역순, 20건.")
    @GetMapping("/users")
    public ApiResponse<Page<UserResponse>> getUsers(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.ok(adminService.getUsers(pageable, search));
    }

    @Operation(summary = "사용자 권한 변경", description = "USER ↔ ADMIN 전환. 자기 자신도 변경 가능.")
    @PatchMapping("/users/{id}/role")
    public ApiResponse<UserResponse> updateRole(
            @PathVariable Long id,
            @RequestBody @Valid RoleUpdateRequest request) {
        return ApiResponse.ok(adminService.updateRole(id, request));
    }

    @Operation(summary = "전체 주문 목록 (페이징)", description = "?status=PENDING|CONFIRMED|CANCELLED, ?userId=숫자 로 필터. 사용자명 포함. 기본: 최신순, 20건.")
    @GetMapping("/orders")
    public ApiResponse<Page<AdminOrderResponse>> getOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Long userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.ok(adminService.getOrders(status, userId, pageable));
    }

    @Operation(summary = "전체 상품 목록 (페이징)", description = "ACTIVE + DISCONTINUED 모두 포함. ?search=키워드 로 상품명/SKU 검색. 기본: 최신순, 20건.")
    @GetMapping("/products")
    public ApiResponse<Page<ProductResponse>> getAllProducts(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.ok(adminService.getAllProducts(pageable, search));
    }

    @Operation(summary = "기간별 일별 주문·매출 통계", description = "?from=YYYY-MM-DD&to=YYYY-MM-DD 로 기간 지정. 최신일 우선 정렬.")
    @GetMapping("/stats/orders")
    public ApiResponse<List<DailyOrderStatsResponse>> getOrderStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(adminService.getOrderStats(from, to));
    }

    @Operation(summary = "특정 날짜 전체 재고 스냅샷", description = "?date=YYYY-MM-DD 로 날짜 지정. 상품명·재고 수량 포함.")
    @GetMapping("/stats/inventory")
    public ApiResponse<List<DailyInventorySnapshotResponse>> getInventorySnapshot(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(adminService.getInventorySnapshot(date));
    }
}
