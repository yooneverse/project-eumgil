package com.ssafy.e102.domain.admin.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ssafy.e102.domain.admin.entity.AdminAreaAssignment;
import com.ssafy.e102.domain.admin.type.AdminAreaAssignmentType;
import com.ssafy.e102.domain.admin.type.AdminAreaWorkStatus;
import com.ssafy.e102.domain.user.entity.User;

public record AdminAreaAssignmentResponse(
	Long assignmentId,
	String gu,
	String dong,
	AdminAreaAssignmentType assignmentType,
	UUID assigneeUserId,
	String assigneeLabel,
	AdminAreaWorkStatus status,
	LocalDateTime updatedAt) {

	public static AdminAreaAssignmentResponse from(AdminAreaAssignment assignment) {
		User assignee = assignment.getAssignee();
		return new AdminAreaAssignmentResponse(
			assignment.getAssignmentId(),
			assignment.getGu(),
			assignment.getDong(),
			assignment.getAssignmentType(),
			assignee == null ? null : assignee.getUserId(),
			assignee == null ? null : assignee.getSocialProvider() + " " + assignee.getSocialProviderUserId(),
			assignment.getStatus(),
			assignment.getUpdatedAt());
	}
}
