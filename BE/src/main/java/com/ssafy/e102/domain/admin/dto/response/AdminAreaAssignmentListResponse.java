package com.ssafy.e102.domain.admin.dto.response;

import java.util.List;

public record AdminAreaAssignmentListResponse(
	List<AdminAreaAssignmentResponse> assignments) {
}
