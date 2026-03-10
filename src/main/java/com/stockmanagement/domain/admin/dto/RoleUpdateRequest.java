package com.stockmanagement.domain.admin.dto;

import com.stockmanagement.domain.user.entity.UserRole;
import jakarta.validation.constraints.NotNull;

public record RoleUpdateRequest(
        @NotNull
        UserRole role
) {
}
