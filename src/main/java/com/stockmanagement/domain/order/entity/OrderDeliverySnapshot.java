package com.stockmanagement.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** 주문 시점 배송지 스냅샷. 원본 배송지가 삭제·변경되어도 주문 당시 주소를 보존한다. */
@Entity
@Table(name = "order_delivery_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderDeliverySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long orderId;

    @Column(nullable = false, length = 50)
    private String recipient;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 10)
    private String zipCode;

    @Column(nullable = false, length = 200)
    private String address1;

    @Column(length = 100)
    private String address2;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private OrderDeliverySnapshot(Long orderId, String recipient, String phone,
                                   String zipCode, String address1, String address2) {
        this.orderId = orderId;
        this.recipient = recipient;
        this.phone = phone;
        this.zipCode = zipCode;
        this.address1 = address1;
        this.address2 = address2;
    }
}
