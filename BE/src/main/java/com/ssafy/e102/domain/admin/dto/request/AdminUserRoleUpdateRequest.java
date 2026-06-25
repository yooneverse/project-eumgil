package com.ssafy.e102.domain.admin.dto.request;

import com.ssafy.e102.domain.user.type.UserRole;

import jakarta.validation.constraints.NotNull;

public record AdminUserRoleUpdateRequest(
	@NotNull
	UserRole role) {
}
