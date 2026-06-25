package com.ssafy.e102.domain.admin.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 검수 구/동 목록 응답")
public record AdminAreaListResponse(
	@Schema(description = "구/동 목록")
	List<AdminAreaResponse> areas) {
}
