package com.stockmanagement.domain.product.notification.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.notification.dto.RestockNotificationResponse;
import com.stockmanagement.domain.product.notification.entity.RestockNotification;
import com.stockmanagement.domain.product.notification.repository.RestockNotificationRepository;
import com.stockmanagement.domain.product.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RestockNotificationService 단위 테스트")
class RestockNotificationServiceTest {

    @Mock private RestockNotificationRepository restockNotificationRepository;
    @Mock private ProductRepository productRepository;

    @InjectMocks private RestockNotificationService restockNotificationService;

    private Product mockProduct(Long id) {
        Product p = Product.builder().name("상품A").price(BigDecimal.valueOf(15000)).sku("SKU-A").build();
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private RestockNotification mockNotification(Long id, Long userId, Long productId) {
        RestockNotification n = RestockNotification.builder().userId(userId).productId(productId).build();
        ReflectionTestUtils.setField(n, "id", id);
        return n;
    }

    @Nested
    @DisplayName("subscribe()")
    class Subscribe {

        @Test
        @DisplayName("정상 구독 → 응답 반환")
        void subscribes() {
            Product product = mockProduct(1L);
            given(productRepository.findById(1L)).willReturn(Optional.of(product));
            given(restockNotificationRepository.existsByUserIdAndProductId(10L, 1L)).willReturn(false);
            RestockNotification saved = mockNotification(100L, 10L, 1L);
            given(restockNotificationRepository.save(any())).willReturn(saved);

            RestockNotificationResponse response = restockNotificationService.subscribe(10L, 1L);

            assertThat(response.getProductId()).isEqualTo(1L);
            assertThat(response.getProductName()).isEqualTo("상품A");
        }

        @Test
        @DisplayName("존재하지 않는 상품 → PRODUCT_NOT_FOUND")
        void productNotFound() {
            given(productRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> restockNotificationService.subscribe(10L, 999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
        }

        @Test
        @DisplayName("이미 구독 중 → RESTOCK_ALREADY_SUBSCRIBED")
        void alreadySubscribed() {
            given(productRepository.findById(1L)).willReturn(Optional.of(mockProduct(1L)));
            given(restockNotificationRepository.existsByUserIdAndProductId(10L, 1L)).willReturn(true);

            assertThatThrownBy(() -> restockNotificationService.subscribe(10L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.RESTOCK_ALREADY_SUBSCRIBED));
        }
    }

    @Nested
    @DisplayName("unsubscribe()")
    class Unsubscribe {

        @Test
        @DisplayName("정상 구독 취소")
        void unsubscribes() {
            restockNotificationService.unsubscribe(10L, 1L);
            verify(restockNotificationRepository).deleteByUserIdAndProductId(10L, 1L);
        }
    }

    @Nested
    @DisplayName("getMyNotifications()")
    class GetMyNotifications {

        @Test
        @DisplayName("구독 목록 반환")
        void returnsNotifications() {
            RestockNotification n1 = mockNotification(1L, 10L, 1L);
            RestockNotification n2 = mockNotification(2L, 10L, 2L);
            given(restockNotificationRepository.findByUserIdOrderByCreatedAtDesc(10L))
                    .willReturn(List.of(n1, n2));

            Product p1 = mockProduct(1L);
            Product p2 = mockProduct(2L);
            ReflectionTestUtils.setField(p2, "name", "상품B");
            given(productRepository.findAllById(List.of(1L, 2L))).willReturn(List.of(p1, p2));

            List<RestockNotificationResponse> result = restockNotificationService.getMyNotifications(10L);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("구독 없으면 빈 목록")
        void emptyList() {
            given(restockNotificationRepository.findByUserIdOrderByCreatedAtDesc(10L))
                    .willReturn(List.of());

            List<RestockNotificationResponse> result = restockNotificationService.getMyNotifications(10L);

            assertThat(result).isEmpty();
        }
    }
}
