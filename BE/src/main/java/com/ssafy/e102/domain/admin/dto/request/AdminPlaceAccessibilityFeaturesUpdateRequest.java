package com.ssafy.e102.domain.admin.dto.request;

import java.util.List;

import com.ssafy.e102.domain.place.type.AccessibilityFeatureType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 장소 접근성 속성 교체 요청")
public record AdminPlaceAccessibilityFeaturesUpdateRequest(
	@Schema(description = "장소 접근성 속성 목록. 요청 목록으로 전체 교체한다.") @NotNull @Size(max = 7)
	List<@NotNull @Valid Feature> features) {

	@Schema(description = "관리자 장소 접근성 속성 항목")
	public record Feature(
		@Schema(description = "접근성 속성 유형", example = "accessibleToilet") @NotNull
		AccessibilityFeatureType featureType,
		@Schema(description = "제공 여부", example = "true") @NotNull
		Boolean isAvailable) {
	}
}
