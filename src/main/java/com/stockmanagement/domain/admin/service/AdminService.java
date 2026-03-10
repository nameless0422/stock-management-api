package com.stockmanagement.domain.admin.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.admin.dto.DashboardResponse;
import com.stockmanagement.domain.admin.dto.LowStockItem;
import com.stockmanagement.domain.admin.dto.RoleUpdateRequest;
import com.stockmanagement.domain.inventory.repository.InventoryRepository;
import com.stockmanagement.domain.order.dto.OrderResponse;
import com.stockmanagement.domain.order.entity.OrderStatus;
import com.stockmanagement.domain.order.repository.OrderRepository;
import com.stockmanagement.domain.user.dto.UserResponse;
import com.stockmanagement.domain.user.entity.User;
import com.stockmanagement.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 관리자 전용 서비스 — 대시보드 통계, 사용자 관리, 전체 주문 조회 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    /** 저재고 경보 기준: available < 이 값이면 경보 목록에 포함 */
    private static final int LOW_STOCK_THRESHOLD = 10;

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final InventoryRepository inventoryRepository;

    /** 관리자 대시보드 — 주문 통계, 매출, 사용자 수, 저재고 목록 */
    public DashboardResponse getDashboard() {
        long totalOrders     = orderRepository.count();
        long pendingOrders   = orderRepository.countByStatus(OrderStatus.PENDING);
        long confirmedOrders = orderRepository.countByStatus(OrderStatus.CONFIRMED);
        long cancelledOrders = orderRepository.countByStatus(OrderStatus.CANCELLED);
        var  totalRevenue    = orderRepository.sumTotalAmountByStatus(OrderStatus.CONFIRMED);
        long totalUsers      = userRepository.count();

        List<LowStockItem> lowStockItems = inventoryRepository
                .findLowStock(LOW_STOCK_THRESHOLD)
                .stream()
                .map(LowStockItem::from)
                .toList();

        return new DashboardResponse(
                totalOrders, pendingOrders, confirmedOrders, cancelledOrders,
                totalRevenue, totalUsers, lowStockItems
        );
    }

    /** 전체 사용자 목록 (페이징) */
    public Page<UserResponse> getUsers(Pageable pageable) {
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

    /** 전체 주문 목록 (페이징, status 필터 선택) */
    public Page<OrderResponse> getOrders(OrderStatus status, Pageable pageable) {
        if (status != null) {
            return orderRepository.findByStatus(status, pageable).map(OrderResponse::from);
        }
        return orderRepository.findAll(pageable).map(OrderResponse::from);
    }
}
