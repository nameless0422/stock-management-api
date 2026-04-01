package com.stockmanagement.domain.admin.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.domain.admin.dto.AdminOrderResponse;
import com.stockmanagement.domain.admin.dto.DashboardResponse;
import com.stockmanagement.domain.admin.dto.RoleUpdateRequest;
import com.stockmanagement.domain.inventory.entity.Inventory;
import com.stockmanagement.domain.inventory.repository.DailyInventorySnapshotRepository;
import com.stockmanagement.domain.inventory.repository.InventoryRepository;
import com.stockmanagement.domain.order.entity.DailyOrderStats;
import com.stockmanagement.domain.order.entity.Order;
import com.stockmanagement.domain.order.entity.OrderStatus;
import com.stockmanagement.domain.order.repository.DailyOrderStatsRepository;
import com.stockmanagement.domain.order.repository.OrderRepository;
import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.admin.setting.service.SystemSettingService;
import com.stockmanagement.domain.product.repository.ProductRepository;
import com.stockmanagement.domain.user.dto.UserResponse;
import com.stockmanagement.domain.user.entity.User;
import com.stockmanagement.domain.user.entity.UserRole;
import com.stockmanagement.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminService 단위 테스트")
class AdminServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private DailyOrderStatsRepository dailyOrderStatsRepository;
    @Mock private UserRepository userRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private DailyInventorySnapshotRepository snapshotRepository;
    @Mock private ProductRepository productRepository;
    @Mock private SystemSettingService systemSettingService;

    @InjectMocks
    private AdminService adminService;

    // ===== getDashboard =====

    @Nested
    @DisplayName("getDashboard()")
    class GetDashboard {

        @Test
        @DisplayName("통계 집계 후 DashboardResponse 반환")
        void returnsDashboard() {
            // 재고 모킹: findLowStock 반환 값이 LowStockItem.from() 내부에서 product 접근
            Product product = mock(Product.class);
            given(product.getId()).willReturn(1L);
            given(product.getName()).willReturn("상품A");
            given(product.getSku()).willReturn("SKU-001");

            Inventory inventory = mock(Inventory.class);
            given(inventory.getProduct()).willReturn(product);
            given(inventory.getOnHand()).willReturn(5);
            given(inventory.getReserved()).willReturn(1);
            given(inventory.getAllocated()).willReturn(1);
            given(inventory.getAvailable()).willReturn(3);

            given(systemSettingService.getLowStockThreshold()).willReturn(10);
            given(orderRepository.count()).willReturn(100L);
            given(orderRepository.countByStatus(OrderStatus.PENDING)).willReturn(10L);
            given(orderRepository.countByStatus(OrderStatus.CONFIRMED)).willReturn(80L);
            given(orderRepository.countByStatus(OrderStatus.CANCELLED)).willReturn(10L);
            given(orderRepository.sumTotalAmountByStatus(OrderStatus.CONFIRMED))
                    .willReturn(BigDecimal.valueOf(500_000));
            given(userRepository.count()).willReturn(200L);
            given(inventoryRepository.findLowStock(10)).willReturn(List.of(inventory));

            DashboardResponse response = adminService.getDashboard();

            assertThat(response.totalOrders()).isEqualTo(100L);
            assertThat(response.pendingOrders()).isEqualTo(10L);
            assertThat(response.confirmedOrders()).isEqualTo(80L);
            assertThat(response.cancelledOrders()).isEqualTo(10L);
            assertThat(response.totalRevenue()).isEqualByComparingTo(BigDecimal.valueOf(500_000));
            assertThat(response.totalUsers()).isEqualTo(200L);
            assertThat(response.lowStockItems()).hasSize(1);
            assertThat(response.lowStockItems().get(0).productName()).isEqualTo("상품A");
        }

        @Test
        @DisplayName("저재고 없으면 빈 목록 반환")
        void emptyLowStock() {
            given(systemSettingService.getLowStockThreshold()).willReturn(10);
            given(orderRepository.count()).willReturn(0L);
            given(orderRepository.countByStatus(any())).willReturn(0L);
            given(orderRepository.sumTotalAmountByStatus(any())).willReturn(BigDecimal.ZERO);
            given(userRepository.count()).willReturn(0L);
            given(inventoryRepository.findLowStock(10)).willReturn(List.of());

            DashboardResponse response = adminService.getDashboard();

            assertThat(response.lowStockItems()).isEmpty();
        }
    }

    // ===== getUsers =====

    @Nested
    @DisplayName("getUsers()")
    class GetUsers {

        @Test
        @DisplayName("search 없으면 전체 사용자 페이징 조회")
        void returnsAllUsers() {
            User user = User.builder().username("user1").password("pw").email("a@b.com").build();
            Pageable pageable = PageRequest.of(0, 10);
            given(userRepository.findAll(pageable)).willReturn(new PageImpl<>(List.of(user)));

            Page<UserResponse> result = adminService.getUsers(pageable, null);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).username()).isEqualTo("user1");
            verify(userRepository, never()).searchByUsernameOrEmail(any(), any());
        }

        @Test
        @DisplayName("search 있으면 username/email 필터 조회")
        void searchFiltersUsers() {
            User user = User.builder().username("user1").password("pw").email("a@b.com").build();
            Pageable pageable = PageRequest.of(0, 10);
            given(userRepository.searchByUsernameOrEmail("user", pageable))
                    .willReturn(new PageImpl<>(List.of(user)));

            Page<UserResponse> result = adminService.getUsers(pageable, "user");

            assertThat(result.getContent()).hasSize(1);
            verify(userRepository, never()).findAll(pageable);
        }

        @Test
        @DisplayName("search가 공백이면 전체 조회")
        void blankSearchReturnsAll() {
            Pageable pageable = PageRequest.of(0, 10);
            given(userRepository.findAll(pageable)).willReturn(Page.empty());

            adminService.getUsers(pageable, "   ");

            verify(userRepository).findAll(pageable);
            verify(userRepository, never()).searchByUsernameOrEmail(any(), any());
        }
    }

    // ===== updateRole =====

    @Nested
    @DisplayName("updateRole()")
    class UpdateRole {

        @Test
        @DisplayName("권한 변경 성공 → UserResponse 반환")
        void updatesRole() {
            User user = User.builder().username("user1").password("pw").email("a@b.com").build();
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            UserResponse result = adminService.updateRole(1L, new RoleUpdateRequest(UserRole.ADMIN));

            assertThat(result.username()).isEqualTo("user1");
            assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
        }

        @Test
        @DisplayName("존재하지 않는 사용자 → BusinessException")
        void throwsIfUserNotFound() {
            given(userRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.updateRole(99L, new RoleUpdateRequest(UserRole.USER)))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ===== getOrders =====

    @Nested
    @DisplayName("getOrders()")
    class GetOrders {

        private Order mockOrder(Long userId) {
            Order order = mock(Order.class);
            given(order.getId()).willReturn(1L);
            given(order.getUserId()).willReturn(userId);
            given(order.getStatus()).willReturn(OrderStatus.CONFIRMED);
            given(order.getTotalAmount()).willReturn(BigDecimal.valueOf(10_000));
            given(order.getCreatedAt()).willReturn(null);
            return order;
        }

        @Test
        @DisplayName("필터 없음 — 전체 주문 반환")
        void returnsAllOrders() {
            Order order = mockOrder(1L);
            Pageable pageable = PageRequest.of(0, 10);
            given(orderRepository.findAll(pageable)).willReturn(new PageImpl<>(List.of(order)));
            User user = User.builder().username("user1").password("pw").email("a@b.com").build();
            given(userRepository.findAllById(anySet())).willReturn(List.of(user));

            Page<AdminOrderResponse> result = adminService.getOrders(null, null, pageable);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("status 필터 — 해당 상태 주문만 반환")
        void filtersByStatus() {
            Order order = mockOrder(1L);
            Pageable pageable = PageRequest.of(0, 10);
            given(orderRepository.findByStatus(OrderStatus.PENDING, pageable))
                    .willReturn(new PageImpl<>(List.of(order)));
            given(userRepository.findAllById(anySet())).willReturn(List.of());

            adminService.getOrders(OrderStatus.PENDING, null, pageable);

            verify(orderRepository).findByStatus(OrderStatus.PENDING, pageable);
        }

        @Test
        @DisplayName("userId 필터 — 해당 사용자 주문만 반환")
        void filtersByUserId() {
            Order order = mockOrder(5L);
            Pageable pageable = PageRequest.of(0, 10);
            given(orderRepository.findByUserId(5L, pageable))
                    .willReturn(new PageImpl<>(List.of(order)));
            given(userRepository.findAllById(anySet())).willReturn(List.of());

            adminService.getOrders(null, 5L, pageable);

            verify(orderRepository).findByUserId(5L, pageable);
        }

        @Test
        @DisplayName("userId + status 복합 필터")
        void filtersByUserIdAndStatus() {
            Order order = mockOrder(5L);
            Pageable pageable = PageRequest.of(0, 10);
            given(orderRepository.findByUserIdAndStatus(5L, OrderStatus.CONFIRMED, pageable))
                    .willReturn(new PageImpl<>(List.of(order)));
            given(userRepository.findAllById(anySet())).willReturn(List.of());

            adminService.getOrders(OrderStatus.CONFIRMED, 5L, pageable);

            verify(orderRepository).findByUserIdAndStatus(5L, OrderStatus.CONFIRMED, pageable);
        }

        @Test
        @DisplayName("사용자 정보 없으면 username = '(알 수 없음)'")
        void unknownUsernameWhenUserNotFound() {
            Order order = mockOrder(99L);
            Pageable pageable = PageRequest.of(0, 10);
            given(orderRepository.findAll(pageable)).willReturn(new PageImpl<>(List.of(order)));
            given(userRepository.findAllById(anySet())).willReturn(List.of());

            Page<AdminOrderResponse> result = adminService.getOrders(null, null, pageable);

            assertThat(result.getContent().get(0).username()).isEqualTo("(알 수 없음)");
        }
    }

    // ===== getAllProducts =====

    @Nested
    @DisplayName("getAllProducts()")
    class GetAllProducts {

        @Test
        @DisplayName("search 없으면 전체 상품 페이징 조회")
        void returnsAllProducts() {
            Pageable pageable = PageRequest.of(0, 10);
            given(productRepository.findAll(pageable)).willReturn(Page.empty());

            adminService.getAllProducts(pageable, null);

            verify(productRepository).findAll(pageable);
            verify(productRepository, never()).searchAll(any(), any());
        }

        @Test
        @DisplayName("search 있으면 상품명/SKU 필터 조회")
        void searchFiltersProducts() {
            Pageable pageable = PageRequest.of(0, 10);
            given(productRepository.searchAll("노트북", pageable)).willReturn(Page.empty());

            adminService.getAllProducts(pageable, "노트북");

            verify(productRepository).searchAll("노트북", pageable);
        }
    }

    // ===== getOrderStats =====

    @Nested
    @DisplayName("getOrderStats()")
    class GetOrderStats {

        @Test
        @DisplayName("기간 내 일별 주문 통계 목록 반환")
        void returnsStats() {
            DailyOrderStats stats = DailyOrderStats.builder()
                    .statDate(LocalDate.of(2025, 1, 1))
                    .totalOrders(10)
                    .confirmedOrders(8)
                    .cancelledOrders(2)
                    .totalRevenue(BigDecimal.valueOf(100_000))
                    .build();
            LocalDate from = LocalDate.of(2025, 1, 1);
            LocalDate to = LocalDate.of(2025, 1, 7);
            given(dailyOrderStatsRepository.findByStatDateBetweenOrderByStatDateDesc(from, to))
                    .willReturn(List.of(stats));

            var result = adminService.getOrderStats(from, to);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatDate()).isEqualTo(LocalDate.of(2025, 1, 1));
        }

        @Test
        @DisplayName("결과 없으면 빈 목록 반환")
        void returnsEmptyList() {
            given(dailyOrderStatsRepository.findByStatDateBetweenOrderByStatDateDesc(any(), any()))
                    .willReturn(List.of());

            assertThat(adminService.getOrderStats(LocalDate.now(), LocalDate.now())).isEmpty();
        }
    }

    // ===== getInventorySnapshot =====

    @Nested
    @DisplayName("getInventorySnapshot()")
    class GetInventorySnapshot {

        @Test
        @DisplayName("특정 날짜 재고 스냅샷 목록 반환")
        void returnsSnapshot() {
            given(snapshotRepository.findBySnapshotDate(any())).willReturn(List.of());

            var result = adminService.getInventorySnapshot(LocalDate.now());

            assertThat(result).isEmpty();
            verify(snapshotRepository).findBySnapshotDate(any());
        }
    }
}
