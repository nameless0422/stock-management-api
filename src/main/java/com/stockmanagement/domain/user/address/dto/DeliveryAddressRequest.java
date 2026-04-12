package com.stockmanagement.domain.user.address.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 배송지 등록 및 수정 요청 DTO. */
@Getter
@NoArgsConstructor
public class DeliveryAddressRequest {

    @NotBlank(message = "배송지 별칭은 필수입니다.")
    @Size(max = 50, message = "별칭은 50자 이하여야 합니다.")
    private String alias;

    @NotBlank(message = "수령인 이름은 필수입니다.")
    @Size(max = 50, message = "수령인 이름은 50자 이하여야 합니다.")
    private String recipient;

    @NotBlank(message = "연락처는 필수입니다.")
    @Pattern(regexp = "^\\d{10,11}$", message = "연락처는 10~11자리 숫자여야 합니다.")
    private String phone;

    @NotBlank(message = "우편번호는 필수입니다.")
    @Size(max = 10, message = "우편번호는 10자 이하여야 합니다.")
    private String zipCode;

    @NotBlank(message = "주소는 필수입니다.")
    @Size(max = 200, message = "주소는 200자 이하여야 합니다.")
    private String address1;

    @Size(max = 100, message = "상세주소는 100자 이하여야 합니다.")
    private String address2;
}
