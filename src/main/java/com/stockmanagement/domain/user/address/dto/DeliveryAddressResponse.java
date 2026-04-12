package com.stockmanagement.domain.user.address.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockmanagement.domain.user.address.entity.DeliveryAddress;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** 배송지 응답 DTO. */
@Getter
@Builder
public class DeliveryAddressResponse {

    private Long id;
    private String alias;
    private String recipient;
    private String phone;
    private String zipCode;
    private String address1;
    private String address2;
    @JsonProperty("isDefault")
    private boolean isDefault;
    private LocalDateTime createdAt;

    public static DeliveryAddressResponse from(DeliveryAddress address) {
        return DeliveryAddressResponse.builder()
                .id(address.getId())
                .alias(address.getAlias())
                .recipient(address.getRecipient())
                .phone(address.getPhone())
                .zipCode(address.getZipCode())
                .address1(address.getAddress1())
                .address2(address.getAddress2())
                .isDefault(address.isDefault())
                .createdAt(address.getCreatedAt())
                .build();
    }
}
