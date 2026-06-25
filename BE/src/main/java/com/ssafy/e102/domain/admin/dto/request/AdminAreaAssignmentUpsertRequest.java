package com.ssafy.e102.domain.admin.dto.request;

import java.util.UUID;

import com.ssafy.e102.domain.admin.type.AdminAreaAssignmentType;
import com.ssafy.e102.domain.admin.type.AdminAreaWorkStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminAreaAssignmentUpsertRequest(
	@NotBlank
	String gu,
	@NotBlank
	String dong,
	@NotNull
	AdminAreaAssignmentType assignmentType,
	UUID assigneeUserId,
	AdminAreaWorkStatus status) {
}
