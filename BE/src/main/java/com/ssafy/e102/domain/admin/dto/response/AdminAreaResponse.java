package com.ssafy.e102.domain.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 검수 구/동 응답")
public record AdminAreaResponse(
	@Schema(description = "구", example = "강서구")
	String gu,
	@Schema(description = "동", example = "명지동")
	String dong) {
}
