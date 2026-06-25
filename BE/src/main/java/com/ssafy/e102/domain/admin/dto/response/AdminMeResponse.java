package com.ssafy.e102.domain.admin.dto.response;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 principal 응답")
public record AdminMeResponse(
	@Schema(description = "관리자 사용자 ID", example = "7dafc215-b297-4f6c-bd7f-bc77fbb421a2")
	UUID userId,
	@Schema(description = "관리자 권한명", example = "ADMIN")
	String role,
	@Schema(description = "관리자 permission 목록", example = "[\"ADMIN_MAP_READ\", \"ADMIN_USER_READ\", \"ADMIN_USER_WRITE\", \"ADMIN_AREA_ASSIGNMENT_READ\", \"ADMIN_AREA_ASSIGNMENT_WRITE\", \"ADMIN_PLACE_READ\", \"ADMIN_PLACE_WRITE\", \"HAZARD_REPORT_READ\", \"HAZARD_REPORT_REVIEW\"]")
	List<String> permissions) {

	public static AdminMeResponse of(UUID userId, List<String> permissions) {
		return new AdminMeResponse(
			userId,
			"ADMIN",
			List.copyOf(permissions));
	}
}
