package com.stockmanagement.domain.user.address.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 배송지 엔티티.
 *
 * <p>사용자({@code userId})별로 여러 배송지를 등록할 수 있으며,
 * {@code isDefault = true}인 배송지가 기본 배송지다.
 * 기본 배송지 전환 시 서비스 레이어에서 기존 기본 배송지를 먼저 해제한다.
 */
@Entity
@Table(name = "delivery_addresses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** 배송지 별칭 (집, 회사 등 사용자 정의 이름) */
    @Column(nullable = false, length = 50)
    private String alias;

    /** 수령인 이름 */
    @Column(nullable = false, length = 50)
    private String recipient;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 10)
    private String zipCode;

    /** 도로명 또는 지번 주소 */
    @Column(nullable = false, length = 200)
    private String address1;

    /** 상세주소 (동·호수 등) */
    @Column(length = 100)
    private String address2;

    /** 기본 배송지 여부 — 사용자당 1개만 true */
    @Column(nullable = false)
    private boolean isDefault;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private DeliveryAddress(Long userId, String alias, String recipient,
                            String phone, String zipCode, String address1, String address2) {
        this.userId = userId;
        this.alias = alias;
        this.recipient = recipient;
        this.phone = phone;
        this.zipCode = zipCode;
        this.address1 = address1;
        this.address2 = address2;
        this.isDefault = false;
    }

    // ===== 비즈니스 메서드 =====

    /** 배송지 정보를 수정한다. */
    public void update(String alias, String recipient, String phone,
                       String zipCode, String address1, String address2) {
        this.alias = alias;
        this.recipient = recipient;
        this.phone = phone;
        this.zipCode = zipCode;
        this.address1 = address1;
        this.address2 = address2;
    }

    /** 기본 배송지로 설정한다. */
    public void setAsDefault() {
        this.isDefault = true;
    }

    /** 기본 배송지 지정을 해제한다. */
    public void unsetDefault() {
        this.isDefault = false;
    }
}
