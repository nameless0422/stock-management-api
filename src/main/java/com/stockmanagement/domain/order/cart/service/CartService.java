package com.stockmanagement.domain.order.cart.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.order.cart.dto.CartCheckoutRequest;
import com.stockmanagement.domain.order.cart.dto.CartItemRequest;
import com.stockmanagement.domain.order.cart.dto.CartResponse;
import com.stockmanagement.domain.order.cart.entity.CartItem;
import com.stockmanagement.domain.order.cart.repository.CartRepository;
import com.stockmanagement.domain.order.dto.OrderCreateRequest;
import com.stockmanagement.domain.order.dto.OrderItemRequest;
import com.stockmanagement.domain.order.dto.OrderResponse;
import com.stockmanagement.domain.order.service.OrderService;
import com.stockmanagement.domain.inventory.entity.Inventory;
import com.stockmanagement.domain.inventory.repository.InventoryRepository;
import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.entity.ProductStatus;
import com.stockmanagement.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 장바구니 비즈니스 로직 서비스.
 *
 * <p>트랜잭션 전략:
 * <ul>
 *   <li>클래스 레벨: {@code @Transactional(readOnly = true)} — 조회 기본값
 *   <li>쓰기 메서드: {@code @Transactional} 로 개별 오버라이드
 * </ul>
 *
 * <p>주문 전환({@link #checkout}) 시 {@link OrderService#create}를 호출하여
 * 재고 예약과 주문 생성을 함께 처리한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final OrderService orderService;

    /** 사용자의 장바구니 전체를 재고 상태 포함하여 조회한다. */
    public CartResponse getCart(Long userId) {
        List<CartItem> items = cartRepository.findByUserId(userId);
        if (items.isEmpty()) {
            return CartResponse.from(userId, items, null);
        }
        List<Long> productIds = items.stream().map(i -> i.getProduct().getId()).toList();
        Map<Long, Integer> availabilityMap = inventoryRepository.findAllByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(inv -> inv.getProduct().getId(), Inventory::getAvailable));
        return CartResponse.from(userId, items, availabilityMap);
    }

    /**
     * 상품을 장바구니에 추가하거나 수량을 변경한다.
     *
     * <p>동일 상품이 이미 담겨 있으면 요청 수량으로 덮어쓴다(교체).
     * 없으면 새 CartItem을 추가한다.
     */
    @Transactional
    public CartResponse addOrUpdate(Long userId, CartItemRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        // 판매 중인 상품만 장바구니에 담을 수 있다
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE);
        }

        Optional<CartItem> existing =
                cartRepository.findByUserIdAndProductId(userId, product.getId());

        if (existing.isPresent()) {
            existing.get().updateQuantity(request.getQuantity());
        } else {
            CartItem item = CartItem.builder()
                    .userId(userId)
                    .product(product)
                    .quantity(request.getQuantity())
                    .build();
            cartRepository.save(item);
        }

        return getCart(userId);
    }

    /**
     * 특정 상품을 장바구니에서 제거한다.
     *
     * @throws BusinessException 장바구니에 해당 상품이 없는 경우
     */
    @Transactional
    public void removeItem(Long userId, Long productId) {
        CartItem item = cartRepository.findByUserIdAndProductId(userId, productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));
        cartRepository.delete(item);
    }

    /** 사용자의 장바구니를 전체 비운다. */
    @Transactional
    public void clear(Long userId) {
        cartRepository.deleteByUserId(userId);
    }

    /**
     * 장바구니를 주문으로 전환한다.
     *
     * <p>처리 흐름:
     * <ol>
     *   <li>장바구니가 비어 있으면 예외
     *   <li>현재 상품 가격 기준으로 {@link OrderCreateRequest} 구성
     *   <li>{@link OrderService#create} 호출 → 재고 예약 + 주문 생성
     *   <li>주문 생성 성공 시 장바구니 비우기
     * </ol>
     *
     * @param userId           주문자 ID
     * @param checkoutRequest  멱등성 키 포함
     * @return 생성된 주문 응답
     */
    @Transactional
    public OrderResponse checkout(Long userId, CartCheckoutRequest checkoutRequest) {
        List<CartItem> items = cartRepository.findByUserId(userId);
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.CART_EMPTY);
        }

        List<OrderItemRequest> orderItems = items.stream()
                .map(item -> OrderItemRequest.of(
                        item.getProduct().getId(),
                        item.getQuantity(),
                        item.getProduct().getPrice()))
                .toList();

        OrderCreateRequest orderRequest = OrderCreateRequest.of(
                userId, checkoutRequest.getIdempotencyKey(), orderItems,
                checkoutRequest.getCouponCode(), checkoutRequest.getUsePoints(),
                checkoutRequest.getDeliveryAddressId());

        OrderResponse orderResponse = orderService.create(orderRequest, userId);

        // 주문 생성 성공 후 장바구니 비우기
        cartRepository.deleteByUserId(userId);

        return orderResponse;
    }
}
