package com.stockmanagement.domain.product.wishlist.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.repository.ProductRepository;
import com.stockmanagement.domain.product.wishlist.dto.WishlistResponse;
import com.stockmanagement.domain.product.wishlist.entity.WishlistItem;
import com.stockmanagement.domain.product.wishlist.repository.WishlistRepository;
import com.stockmanagement.domain.user.entity.User;
import com.stockmanagement.domain.user.repository.UserRepository;
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
@DisplayName("WishlistService 단위 테스트")
class WishlistServiceTest {

    @Mock private WishlistRepository wishlistRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private WishlistService wishlistService;

    private User mockUser(Long id) {
        User u = User.builder().username("user1").password("pw").email("e@e.com").build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private Product mockProduct(Long id) {
        Product p = Product.builder().name("상품A").price(BigDecimal.valueOf(15000)).sku("SKU-A").build();
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private WishlistItem mockItem(Long id, Long userId, Long productId) {
        WishlistItem item = WishlistItem.builder().userId(userId).productId(productId).build();
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    @Nested
    @DisplayName("add() — 위시리스트 추가")
    class Add {

        @Test
        @DisplayName("정상 추가 → 저장 호출")
        void success() {
            given(userRepository.findByUsername("user1")).willReturn(Optional.of(mockUser(1L)));
            given(productRepository.findById(10L)).willReturn(Optional.of(mockProduct(10L)));
            given(wishlistRepository.existsByUserIdAndProductId(1L, 10L)).willReturn(false);
            WishlistItem saved = mockItem(5L, 1L, 10L);
            given(wishlistRepository.save(any())).willReturn(saved);

            WishlistResponse response = wishlistService.add(10L, "user1");

            assertThat(response.getProductId()).isEqualTo(10L);
            verify(wishlistRepository).save(any());
        }

        @Test
        @DisplayName("이미 추가된 상품 → WISHLIST_ALREADY_EXISTS")
        void alreadyExists() {
            given(userRepository.findByUsername("user1")).willReturn(Optional.of(mockUser(1L)));
            given(productRepository.findById(10L)).willReturn(Optional.of(mockProduct(10L)));
            given(wishlistRepository.existsByUserIdAndProductId(1L, 10L)).willReturn(true);

            assertThatThrownBy(() -> wishlistService.add(10L, "user1"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WISHLIST_ALREADY_EXISTS);
        }
    }

    @Nested
    @DisplayName("remove() — 위시리스트 제거")
    class Remove {

        @Test
        @DisplayName("정상 제거 → delete 호출")
        void success() {
            given(userRepository.findByUsername("user1")).willReturn(Optional.of(mockUser(1L)));
            WishlistItem item = mockItem(5L, 1L, 10L);
            given(wishlistRepository.findByUserIdAndProductId(1L, 10L)).willReturn(Optional.of(item));

            wishlistService.remove(10L, "user1");

            verify(wishlistRepository).delete(item);
        }

        @Test
        @DisplayName("존재하지 않는 항목 → WISHLIST_ITEM_NOT_FOUND")
        void notFound() {
            given(userRepository.findByUsername("user1")).willReturn(Optional.of(mockUser(1L)));
            given(wishlistRepository.findByUserIdAndProductId(1L, 10L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> wishlistService.remove(10L, "user1"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WISHLIST_ITEM_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getList() — 위시리스트 조회")
    class GetList {

        @Test
        @DisplayName("목록 반환")
        void success() {
            given(userRepository.findByUsername("user1")).willReturn(Optional.of(mockUser(1L)));
            WishlistItem item = mockItem(5L, 1L, 10L);
            given(wishlistRepository.findByUserIdOrderByCreatedAtDesc(1L)).willReturn(List.of(item));
            given(productRepository.findById(10L)).willReturn(Optional.of(mockProduct(10L)));

            List<WishlistResponse> list = wishlistService.getList("user1");

            assertThat(list).hasSize(1);
            assertThat(list.get(0).getProductName()).isEqualTo("상품A");
        }
    }
}
