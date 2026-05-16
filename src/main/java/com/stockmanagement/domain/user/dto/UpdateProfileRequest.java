package com.stockmanagement.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 프로필 수정 요청 DTO. 변경할 필드만 포함한�� (null이면 변경하�� 않음). */
@Getter
@NoArgsConstructor
public class UpdateProfileRequest {

    @Email(message = "올바른 이메일 형식이 아��니다.")
    @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
    private String email;

    @Pattern(regexp = "^[0-9\\-]{10,20}$", message = "전화번호 형식이 올바르지 않습니다.")
    private String phoneNumber;
}
