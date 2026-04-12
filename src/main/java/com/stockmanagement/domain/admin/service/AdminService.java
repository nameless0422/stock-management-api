package com.stockmanagement.domain.admin.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.admin.dto.AdminOrderResponse;
import com.stockmanagement.domain.admin.dto.DashboardResponse;
import com.stockmanagement.domain.admin.dto.LowStockItem;
import com.stockmanagement.domain.admin.dto.RoleUpdateRequest;
import com.stockmanagement.domain.admin.setting.service.SystemSettingService;
import com.stockmanagement.domain.inventory.dto.DailyInventorySnapshotResponse;
import com.stockmanagement.domain.inventory.repository.DailyInventorySnapshotRepository;
import com.stockmanagement.domain.inventory.repository.InventoryRepository;
import com.stockmanagement.domain.order.dto.DailyOrderStatsResponse;
import com.stockmanagement.domain.order.entity.Order;
import com.stockmanagement.domain.order.entity.OrderStatus;
import com.stockmanagement.domain.order.repository.DailyOrderStatsRepository;
import com.stockmanagement.domain.order.repository.OrderRepository;
import com.stockmanagement.domain.order.repository.OrderStatsProjection;
import com.stockmanagement.domain.product.dto.ProductResponse;
import com.stockmanagement.domain.product.repository.ProductRepository;
import com.stockmanagement.domain.user.dto.UserResponse;
import com.stockmanagement.domain.user.entity.User;
import com.stockmanagement.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 관리자 전용 서비스 — 대시보드 통계, 사용자 관리, 전체 주문 조회 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private final OrderRepository orderRepository;
    private final DailyOrderStatsRepository dailyOrderStatsRepository;
    private final UserRepository userRepository;
    private final InventoryRepository inventoryRepository;
    private final DailyInventorySnapshotRepository snapshotRepository;
    private final ProductRepository productRepository;
    private final SystemSettingService systemSettingService;

    /**
     * 관리자 대시보드 — 주문 통계, 매출, 사용자 수, 저재고 목록.
     *
     * <p>기존 6개 쿼리(상태별 count×4 + sum×1 + userCount×1)를 2개로 줄인다.
     * {@code findOrderStats()}의 GROUP BY 결과로 주문 관련 5개 쿼리를 대체한다.
     */
    public DashboardResponse getDashboard() {
        // 쿼리 1: 상태별 주문 수·금액 일괄 집계
        Map<OrderStatus, OrderStatsProjection> statsMap = orderRepository.findOrderStats()
                .stream()
                .collect(Collectors.toMap(OrderStatsProjection::getStatus, s -> s));

        long totalOrders     = statsMap.values().stream().mapToLong(OrderStatsProjection::getOrderCount).sum();
        long pendingOrders   = countFromMap(statsMap, OrderStatus.PENDING);
        long confirmedOrders = countFromMap(statsMap, OrderStatus.CONFIRMED);
        long cancelledOrders = countFromMap(statsMap, OrderStatus.CANCELLED);
        var  totalRevenue    = statsMap.containsKey(OrderStatus.CONFIRMED)
                ? statsMap.get(OrderStatus.CONFIRMED).getTotalAmount()
                : java.math.BigDecimal.ZERO;

        // 쿼리 2: 전체 사용자 수
        long totalUsers = userRepository.count();

        List<LowStockItem> lowStockItems = inventoryRepository
                .findLowStock(systemSettingService.getLowStockThreshold())
                .stream()
                .map(LowStockItem::from)
                .toList();

        return new DashboardResponse(
                totalOrders, pendingOrders, confirmedOrders, cancelledOrders,
                totalRevenue, totalUsers, lowStockItems
        );
    }

    private long countFromMap(Map<OrderStatus, OrderStatsProjection> statsMap, OrderStatus status) {
        OrderStatsProjection proj = statsMap.get(status);
        return proj != null ? proj.getOrderCount() : 0L;
    }

    /** 전체 사용자 목록 (페이징). search가 있으면 username/email로 필터링 */
    public Page<UserResponse> getUsers(Pageable pageable, String search) {
        if (search != null && !search.isBlank()) {
            return userRepository.searchByUsernameOrEmail(search, pageable).map(UserResponse::from);
        }
        return userRepository.findAll(pageable).map(UserResponse::from);
    }

    /** 사용자 권한 변경 */
    @Transactional
    public UserResponse updateRole(Long userId, RoleUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.changeRole(request.role());
        return UserResponse.from(user);
    }

    /** 전체 주문 목록 (페이징, status·userId 필터 선택). 사용자명(username) 포함 */
    public Page<AdminOrderResponse> getOrders(OrderStatus status, Long userId, Pageable pageable) {
        Page<Order> orders;
        if (userId != null && status != null) {
            orders = orderRepository.findByUserIdAndStatus(userId, status, pageable);
        } else if (userId != null) {
            orders = orderRepository.findByUserId(userId, pageable);
        } else if (status != null) {
            orders = orderRepository.findByStatus(status, pageable);
        } else {
            orders = orderRepository.findAll(pageable);
        }

        // userId 목록으로 사용자명 일괄 조회
        Set<Long> userIds = orders.stream().map(Order::getUserId).collect(Collectors.toSet());
        Map<Long, String> usernameMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));

        return orders.map(o -> AdminOrderResponse.from(o, usernameMap.getOrDefault(o.getUserId(), "(알 수 없음)")));
    }

    /** 전체 상품 목록 (ACTIVE + DISCONTINUED, 관리자 전용). search가 있으면 상품명/SKU로 필터링 */
    public Page<ProductResponse> getAllProducts(Pageable pageable, String search) {
        if (search != null && !search.isBlank()) {
            return productRepository.searchAll(search, pageable).map(ProductResponse::from);
        }
        return productRepository.findAll(pageable).map(ProductResponse::from);
    }

    /** 기간별 일별 주문·매출 통계 조회 (최신일 우선) */
    public List<DailyOrderStatsResponse> getOrderStats(LocalDate from, LocalDate to) {
        return dailyOrderStatsRepository
                .findByStatDateBetweenOrderByStatDateDesc(from, to)
                .stream().map(DailyOrderStatsResponse::from).toList();
    }

    /** 특정 날짜의 전체 재고 스냅샷 조회 */
    public List<DailyInventorySnapshotResponse> getInventorySnapshot(LocalDate date) {
        return snapshotRepository.findBySnapshotDate(date)
                .stream().map(DailyInventorySnapshotResponse::from).toList();
    }
}
