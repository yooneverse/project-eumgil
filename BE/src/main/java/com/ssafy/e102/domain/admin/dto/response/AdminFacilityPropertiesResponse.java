package com.ssafy.e102.domain.admin.dto.response;

import com.ssafy.e102.domain.place.type.PlaceCategory;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 편의시설 속성")
public record AdminFacilityPropertiesResponse(
	@Schema(description = "장소 ID", example = "1")
	String placeId,
	@Schema(description = "장소명", example = "부산시청")
	String name,
	@Schema(description = "장소 카테고리", example = "PUBLIC_OFFICE")
	PlaceCategory category,
	@Schema(description = "주소")
	String address,
	@Schema(description = "외부 provider 장소 ID")
	String providerPlaceId) {
}
