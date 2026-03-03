package com.stockmanagement.domain.product.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.product.dto.ProductCreateRequest;
import com.stockmanagement.domain.product.dto.ProductResponse;
import com.stockmanagement.domain.product.dto.ProductUpdateRequest;
import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.entity.ProductStatus;
import com.stockmanagement.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 비즈니스 로직 서비스.
 *
 * <p>트랜잭션 전략:
 * <ul>
 *   <li>클래스 레벨: {@code @Transactional(readOnly = true)} — 조회 성능 최적화 기본값
 *   <li>쓰기 메서드: {@code @Transactional} 으로 개별 오버라이드
 * </ul>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    /**
     * 상품을 등록한다.
     * SKU 중복 여부를 먼저 확인해 충돌을 방지한다.
     */
    @Transactional
    public ProductResponse create(ProductCreateRequest request) {
        if (productRepository.existsBySku(request.getSku())) {
            throw new BusinessException(ErrorCode.DUPLICATE_SKU);
        }
        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .sku(request.getSku())
                .category(request.getCategory())
                .build();
        return ProductResponse.from(productRepository.save(product));
    }

    /** 단건 조회 — 존재하지 않으면 404 예외 */
    public ProductResponse getById(Long id) {
        return ProductResponse.from(findById(id));
    }

    /** ACTIVE 상태인 상품만 페이징 조회 */
    public Page<ProductResponse> getList(Pageable pageable) {
        return productRepository.findByStatus(ProductStatus.ACTIVE, pageable)
                .map(ProductResponse::from);
    }

    /**
     * 상품 정보를 수정한다.
     * 더티 체킹(dirty checking)으로 별도 save() 호출 없이 UPDATE가 수행된다.
     */
    @Transactional
    public ProductResponse update(Long id, ProductUpdateRequest request) {
        Product product = findById(id);
        product.update(request.getName(), request.getDescription(),
                request.getPrice(), request.getCategory());
        return ProductResponse.from(product);
    }

    /**
     * 상품을 삭제한다 (소프트 삭제).
     * 실제 DELETE 대신 status를 DISCONTINUED로 변경해 데이터를 보존한다.
     */
    @Transactional
    public void delete(Long id) {
        Product product = findById(id);
        product.changeStatus(ProductStatus.DISCONTINUED);
    }

    /** 공통 조회 헬퍼 — 없으면 PRODUCT_NOT_FOUND 예외 발생 */
    private Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }
}
