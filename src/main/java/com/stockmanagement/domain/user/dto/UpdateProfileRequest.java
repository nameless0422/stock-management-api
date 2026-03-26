package com.stockmanagement.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 프로필 수정 요청 DTO. 변경할 필드만 포함한다 (null이면 변경하지 않음). */
@Getter
@NoArgsConstructor
public class UpdateProfileRequest {

    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;
}
