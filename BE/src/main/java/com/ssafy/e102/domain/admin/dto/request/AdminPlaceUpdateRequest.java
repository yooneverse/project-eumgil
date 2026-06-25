package com.ssafy.e102.domain.admin.dto.request;

import com.ssafy.e102.domain.place.type.PlaceCategory;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 장소 기본 정보 수정 요청")
public record AdminPlaceUpdateRequest(
	@Schema(description = "장소명. null이면 기존 값을 유지한다.", example = "부산시청") @Size(max = 255)
	String name,
	@Schema(description = "서비스 장소 카테고리. null이면 기존 값을 유지한다.", example = "PUBLIC_OFFICE")
	PlaceCategory category,
	@Schema(description = "주소. null이면 기존 값을 유지하고, 빈 문자열이면 주소를 제거한다.", example = "부산광역시 연제구 중앙대로 1001") @Size(max = 255)
	String address,
	@Schema(description = "장소 좌표. null이면 기존 값을 유지한다.") @Valid
	GeoPointRequest point,
	@Schema(description = "외부 provider 장소 ID. null이면 기존 값을 유지하고, 빈 문자열이면 providerPlaceId를 제거한다.", example = "12345") @Size(max = 100)
	String providerPlaceId) {
}
