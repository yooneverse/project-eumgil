package com.ssafy.e102.domain.admin.dto.request;

import com.ssafy.e102.domain.admin.type.AdminAreaWorkStatus;

import jakarta.validation.constraints.NotNull;

public record AdminAreaAssignmentStatusUpdateRequest(
	@NotNull
	AdminAreaWorkStatus status) {
}
