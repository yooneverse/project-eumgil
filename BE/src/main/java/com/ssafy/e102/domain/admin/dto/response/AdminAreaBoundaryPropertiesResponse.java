package com.ssafy.e102.domain.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 행정구 경계 properties")
public record AdminAreaBoundaryPropertiesResponse(
	@Schema(description = "구", example = "동구")
	String gu,
	@Schema(description = "범위", example = "전체")
	String dong) {
}
