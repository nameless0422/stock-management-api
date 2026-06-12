package com.stockmanagement.domain.order.cart.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.admin.setting.service.SystemSettingService;
import com.stockmanagement.domain.order.cart.dto.CartCheckoutRequest;
import com.stockmanagement.domain.order.cart.dto.CartItemRequest;
import com.stockmanagement.domain.order.cart.dto.CartItemResponse;
import com.stockmanagement.domain.order.cart.dto.CartResponse;
import com.stockmanagement.domain.order.cart.entity.CartItem;
import com.stockmanagement.domain.order.cart.repository.CartRepository;
import com.stockmanagement.domain.order.dto.OrderCreateRequest;
import com.stockmanagement.domain.order.dto.OrderItemRequest;
import com.stockmanagement.domain.order.dto.OrderResponse;
import com.stockmanagement.domain.order.service.OrderCommandService;
import com.stockmanagement.domain.inventory.entity.Inventory;
import com.stockmanagement.domain.inventory.entity.StockStatus;
import com.stockmanagement.domain.inventory.repository.InventoryRepository;
import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.entity.ProductStatus;
import com.stockmanagement.domain.product.entity.ProductVariant;
import com.stockmanagement.domain.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
 * <p>주문 전환({@link #checkout}) 시 {@link OrderCommandService#create}를 호출하여
 * 재고 예약과 주문 생성을 함께 처리한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryRepository inventoryRepository;
    private final OrderCommandService orderCommandService;
    private final SystemSettingService systemSettingService;

    /** 사용자의 장바구니 전체를 재고 상태 포함하여 조회한다. */
    public CartResponse getCart(Long userId) {
        List<CartItem> items = cartRepository.findByUserId(userId);
        if (items.isEmpty()) {
            return CartResponse.from(userId, items);
        }
        int threshold = systemSettingService.getLowStockThreshold();
        List<Long> variantIds = items.stream().map(i -> i.getVariant().getId()).toList();
        Map<Long, Integer> availabilityMap = inventoryRepository.findAllByVariantIdIn(variantIds).stream()
                .collect(Collectors.toMap(inv -> inv.getVariant().getId(), Inventory::getAvailable));
        Map<Long, StockStatus> stockStatusMap = availabilityMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> StockStatus.of(e.getValue(), threshold)));
        return CartResponse.from(userId, items, availabilityMap, stockStatusMap);
    }

    /**
     * 상품 변형을 장바구니에 추가하거나 수량을 변경한다.
     *
     * <p>동일 변형이 이미 담겨 있으면 요청 수량으로 덮어쓴다(교체).
     * 없으면 새 CartItem을 추가한다.
     */
    @Transactional
    public CartItemResponse addOrUpdate(Long userId, CartItemRequest request) {
        ProductVariant variant = variantRepository.findByIdWithProduct(request.getVariantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND));

        Product product = variant.getProduct();

        // 판매 중인 상품/변형만 장바구니에 담을 수 있다
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE);
        }
        if (variant.getStatus() != ProductStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.VARIANT_NOT_AVAILABLE);
        }

        Optional<CartItem> existing =
                cartRepository.findByUserIdAndVariantId(userId, variant.getId());

        CartItem cartItem;
        if (existing.isPresent()) {
            cartItem = existing.get();
            cartItem.updateQuantity(request.getQuantity());
        } else {
            try {
                cartItem = cartRepository.saveAndFlush(CartItem.builder()
                        .userId(userId)
                        .product(product)
                        .variant(variant)
                        .quantity(request.getQuantity())
                        .savedPrice(variant.getPrice())
                        .build());
            } catch (DataIntegrityViolationException e) {
                // 동시 요청으로 UK(user_id, variant_id) 충돌 — 재조회 후 수량 업데이트
                cartItem = cartRepository.findByUserIdAndVariantId(userId, variant.getId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));
                cartItem.updateQuantity(request.getQuantity());
            }
        }

        // 단건 재고 조회 (전체 장바구니 리로드 불필요)
        Integer available = inventoryRepository.findByVariantId(variant.getId())
                .map(Inventory::getAvailable)
                .orElse(null);
        StockStatus stockStatus = available != null
                ? StockStatus.of(available, systemSettingService.getLowStockThreshold()) : null;
        return CartItemResponse.from(cartItem, available, stockStatus);
    }

    /**
     * 특정 변형을 장바구니에서 제거한다.
     *
     * @throws BusinessException 장바구니에 해당 변형이 없는 경우
     */
    @Transactional
    public void removeItem(Long userId, Long variantId) {
        CartItem item = cartRepository.findByUserIdAndVariantId(userId, variantId)
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
     *   <li>현재 variant 가격 기준으로 {@link OrderCreateRequest} 구성
     *   <li>{@link OrderCommandService#create} 호출 → 재고 예약 + 주문 생성
     *   <li>주문 생성 성공 시 장바구니에서 결제된 항목 제거
     * </ol>
     */
    @Transactional
    public OrderResponse checkout(Long userId, CartCheckoutRequest checkoutRequest) {
        List<CartItem> allItems = cartRepository.findByUserId(userId);
        if (allItems.isEmpty()) {
            throw new BusinessException(ErrorCode.CART_EMPTY);
        }

        // selectedVariantIds가 없으면 전체, 있으면 해당 변형만 결제
        List<Long> selected = checkoutRequest.getSelectedVariantIds();
        List<CartItem> items = (selected == null || selected.isEmpty())
                ? allItems
                : allItems.stream()
                        .filter(i -> selected.contains(i.getVariant().getId()))
                        .toList();

        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.CART_EMPTY);
        }

        List<OrderItemRequest> orderItems = items.stream()
                .map(item -> OrderItemRequest.of(
                        item.getProduct().getId(),
                        item.getVariant().getId(),
                        item.getQuantity(),
                        item.getVariant().getPrice()))
                .toList();

        OrderCreateRequest orderRequest = OrderCreateRequest.of(
                userId, checkoutRequest.getIdempotencyKey(), orderItems,
                checkoutRequest.getCouponCode(), checkoutRequest.getUsePoints(),
                checkoutRequest.getDeliveryAddressId());

        OrderResponse orderResponse = orderCommandService.create(orderRequest, userId);

        // 결제한 변형만 장바구니에서 제거 (선택 결제 시 나머지 유지)
        Set<Long> checkedOutVariantIds = items.stream()
                .map(i -> i.getVariant().getId())
                .collect(Collectors.toSet());
        cartRepository.deleteByUserIdAndVariantIdIn(userId, checkedOutVariantIds);

        return orderResponse;
    }
}
