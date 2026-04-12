package com.stockmanagement.domain.order.cart.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.order.cart.dto.CartCheckoutRequest;
import com.stockmanagement.domain.order.cart.dto.CartItemRequest;
import com.stockmanagement.domain.order.cart.dto.CartResponse;
import com.stockmanagement.domain.order.cart.entity.CartItem;
import com.stockmanagement.domain.inventory.repository.InventoryRepository;
import com.stockmanagement.domain.order.cart.repository.CartRepository;
import com.stockmanagement.domain.order.dto.OrderResponse;
import com.stockmanagement.domain.order.service.OrderService;
import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartService 단위 테스트")
class CartServiceTest {

    @Mock CartRepository cartRepository;
    @Mock ProductRepository productRepository;
    @Mock InventoryRepository inventoryRepository;
    @Mock OrderService orderService;

    @InjectMocks CartService cartService;

    private Product product;
    private CartItem cartItem;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .name("상품A").sku("SKU-A").price(BigDecimal.valueOf(1000))
                .build();

        cartItem = CartItem.builder()
                .userId(1L).product(product).quantity(2)
                .build();
    }

    @Nested
    @DisplayName("장바구니 조회")
    class GetCart {

        @Test
        @DisplayName("아이템이 있으면 totalAmount를 계산해서 반환한다")
        void returnsCartWithTotalAmount() {
            given(cartRepository.findByUserId(1L)).willReturn(List.of(cartItem));

            CartResponse response = cartService.getCart(1L);

            assertThat(response.getUserId()).isEqualTo(1L);
            assertThat(response.getItems()).hasSize(1);
            assertThat(response.getTotalAmount()).isEqualByComparingTo("2000");
        }

        @Test
        @DisplayName("빈 장바구니이면 totalAmount = 0")
        void emptyCartReturnsZeroTotal() {
            given(cartRepository.findByUserId(1L)).willReturn(List.of());

            CartResponse response = cartService.getCart(1L);

            assertThat(response.getItems()).isEmpty();
            assertThat(response.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("상품 추가/수량 변경")
    class AddOrUpdate {

        @Test
        @DisplayName("기존에 없는 상품 → 새 CartItem 저장")
        void addsNewItem() {
            CartItemRequest request = mockCartItemRequest(1L, 3);
            given(productRepository.findById(1L)).willReturn(Optional.of(product));
            given(cartRepository.findByUserIdAndProductId(anyLong(), any())).willReturn(Optional.empty());
            given(cartRepository.findByUserId(1L)).willReturn(List.of(cartItem));

            cartService.addOrUpdate(1L, request);

            verify(cartRepository).save(any(CartItem.class));
        }

        @Test
        @DisplayName("이미 담긴 상품 → 수량만 업데이트")
        void updatesExistingItem() {
            CartItemRequest request = mockCartItemRequest(1L, 5);
            given(productRepository.findById(1L)).willReturn(Optional.of(product));
            given(cartRepository.findByUserIdAndProductId(eq(1L), any())).willReturn(Optional.of(cartItem));
            given(cartRepository.findByUserId(1L)).willReturn(List.of(cartItem));

            cartService.addOrUpdate(1L, request);

            verify(cartRepository, never()).save(any());
            assertThat(cartItem.getQuantity()).isEqualTo(5);
        }

        @Test
        @DisplayName("존재하지 않는 상품 → PRODUCT_NOT_FOUND 예외")
        void throwsWhenProductNotFound() {
            CartItemRequest request = mockCartItemRequest(999L, 1);
            given(productRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.addOrUpdate(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ErrorCode.PRODUCT_NOT_FOUND.getMessage());
        }
    }

    @Nested
    @DisplayName("특정 상품 제거")
    class RemoveItem {

        @Test
        @DisplayName("담긴 상품 제거 성공")
        void removesExistingItem() {
            given(cartRepository.findByUserIdAndProductId(1L, 1L)).willReturn(Optional.of(cartItem));

            cartService.removeItem(1L, 1L);

            verify(cartRepository).delete(cartItem);
        }

        @Test
        @DisplayName("담기지 않은 상품 제거 → CART_ITEM_NOT_FOUND 예외")
        void throwsWhenItemNotFound() {
            given(cartRepository.findByUserIdAndProductId(1L, 999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.removeItem(1L, 999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ErrorCode.CART_ITEM_NOT_FOUND.getMessage());
        }
    }

    @Nested
    @DisplayName("장바구니 전환(checkout)")
    class Checkout {

        @Test
        @DisplayName("주문 전환 성공 → 장바구니 비워짐")
        void checkoutClearsCart() {
            given(cartRepository.findByUserId(1L)).willReturn(List.of(cartItem));
            given(orderService.create(any(), anyLong())).willReturn(mock(OrderResponse.class));

            CartCheckoutRequest request = mockCheckoutRequest("key-1");
            cartService.checkout(1L, request);

            verify(orderService).create(any(), anyLong());
            verify(cartRepository).deleteByUserId(1L);
        }

        @Test
        @DisplayName("빈 장바구니에서 주문 전환 → CART_EMPTY 예외")
        void throwsWhenCartEmpty() {
            given(cartRepository.findByUserId(1L)).willReturn(List.of());

            assertThatThrownBy(() -> cartService.checkout(1L, mockCheckoutRequest("key-2")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ErrorCode.CART_EMPTY.getMessage());
        }
    }

    // ===== 헬퍼 =====

    private CartItemRequest mockCartItemRequest(Long productId, int quantity) {
        CartItemRequest req = new CartItemRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(req, "productId", productId);
        org.springframework.test.util.ReflectionTestUtils.setField(req, "quantity", quantity);
        return req;
    }

    private CartCheckoutRequest mockCheckoutRequest(String key) {
        CartCheckoutRequest req = new CartCheckoutRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(req, "idempotencyKey", key);
        return req;
    }
}
