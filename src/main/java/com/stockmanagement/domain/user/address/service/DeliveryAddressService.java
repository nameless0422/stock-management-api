package com.stockmanagement.domain.user.address.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.user.address.dto.DeliveryAddressRequest;
import com.stockmanagement.domain.user.address.dto.DeliveryAddressResponse;
import com.stockmanagement.domain.user.address.entity.DeliveryAddress;
import com.stockmanagement.domain.user.address.repository.DeliveryAddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 배송지 비즈니스 로직 서비스.
 *
 * <p>기본 배송지 전환 규칙:
 * <ul>
 *   <li>첫 번째 배송지 등록 시 자동으로 기본 배송지로 설정
 *   <li>{@link #setDefault} 호출 시 기존 기본 배송지 {@code unsetDefault()} → 신규 {@code setAsDefault()}
 *   <li>기본 배송지 삭제 시 가장 최근 등록된 다른 배송지를 자동으로 기본으로 승격
 * </ul>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DeliveryAddressService {

    private final DeliveryAddressRepository repository;

    /** 사용자의 배송지 목록을 조회한다. 기본 배송지가 맨 앞에 온다. */
    public List<DeliveryAddressResponse> getList(Long userId) {
        return repository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId)
                .stream().map(DeliveryAddressResponse::from).toList();
    }

    /**
     * 배송지 단건을 조회한다.
     *
     * @throws BusinessException 배송지가 없거나 본인 소유가 아닌 경우
     */
    public DeliveryAddressResponse getById(Long id, Long userId) {
        DeliveryAddress address = findAndVerifyOwnership(id, userId);
        return DeliveryAddressResponse.from(address);
    }

    /**
     * 배송지를 등록한다.
     *
     * <p>첫 번째 배송지이면 자동으로 기본 배송지로 설정한다.
     */
    @Transactional
    public DeliveryAddressResponse create(Long userId, DeliveryAddressRequest request) {
        DeliveryAddress address = DeliveryAddress.builder()
                .userId(userId)
                .alias(request.getAlias())
                .recipient(request.getRecipient())
                .phone(request.getPhone())
                .zipCode(request.getZipCode())
                .address1(request.getAddress1())
                .address2(request.getAddress2())
                .build();

        long count = repository.countByUserId(userId);
        // 배송지 최대 20개 제한 — 무제한 등록으로 인한 DB 비대화 방지
        if (count >= 20) {
            throw new BusinessException(ErrorCode.DELIVERY_ADDRESS_LIMIT_EXCEEDED);
        }
        // 첫 번째 배송지 → 자동 기본 배송지 설정
        if (count == 0) {
            address.setAsDefault();
        }

        return DeliveryAddressResponse.from(repository.save(address));
    }

    /**
     * 배송지 정보를 수정한다.
     *
     * @throws BusinessException 배송지가 없거나 본인 소유가 아닌 경우
     */
    @Transactional
    public DeliveryAddressResponse update(Long id, Long userId, DeliveryAddressRequest request) {
        DeliveryAddress address = findAndVerifyOwnership(id, userId);
        address.update(request.getAlias(), request.getRecipient(), request.getPhone(),
                request.getZipCode(), request.getAddress1(), request.getAddress2());
        return DeliveryAddressResponse.from(address);
    }

    /**
     * 기본 배송지를 변경한다.
     *
     * <p>기존 기본 배송지의 {@code isDefault}를 false로 해제한 뒤
     * 요청된 배송지를 기본으로 설정한다.
     *
     * @throws BusinessException 배송지가 없거나 본인 소유가 아닌 경우
     */
    @Transactional
    public DeliveryAddressResponse setDefault(Long id, Long userId) {
        // 비관적 락으로 기존 기본 배송지 조회 — 동시 호출 시 isDefault=true 중복 방지
        repository.findByUserIdAndIsDefaultTrueForUpdate(userId)
                .ifPresent(DeliveryAddress::unsetDefault);

        DeliveryAddress target = findAndVerifyOwnership(id, userId);
        target.setAsDefault();
        return DeliveryAddressResponse.from(target);
    }

    /**
     * 배송지를 삭제한다.
     *
     * <p>기본 배송지 삭제 시 가장 최근 등록된 다른 배송지를 자동으로 기본으로 승격한다.
     *
     * @throws BusinessException 배송지가 없거나 본인 소유가 아닌 경우
     */
    @Transactional
    public void delete(Long id, Long userId) {
        DeliveryAddress address = findAndVerifyOwnership(id, userId);
        boolean wasDefault = address.isDefault();
        repository.delete(address);

        if (wasDefault) {
            // 남은 배송지 중 가장 최근 배송지 1건만 조회하여 기본으로 승격 (전체 목록 조회 불필요)
            repository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                    .ifPresent(DeliveryAddress::setAsDefault);
        }
    }

    // ===== 내부 헬퍼 =====

    private DeliveryAddress findAndVerifyOwnership(Long id, Long userId) {
        DeliveryAddress address = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_ADDRESS_NOT_FOUND));
        if (!address.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.DELIVERY_ADDRESS_ACCESS_DENIED);
        }
        return address;
    }
}
