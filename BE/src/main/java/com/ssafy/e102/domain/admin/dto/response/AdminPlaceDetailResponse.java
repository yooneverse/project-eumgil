package com.ssafy.e102.domain.admin.dto.response;

import java.util.Comparator;
import java.util.List;

import com.ssafy.e102.domain.place.dto.response.PlaceAccessibilityFeatureResponse;
import com.ssafy.e102.domain.place.entity.Place;
import com.ssafy.e102.domain.place.entity.PlaceAccessibilityFeature;
import com.ssafy.e102.domain.place.type.PlaceCategory;
import com.ssafy.e102.global.geo.GeoPointConverter;
import com.ssafy.e102.global.geo.dto.GeoPointResponse;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 장소 상세 응답")
public record AdminPlaceDetailResponse(
	@Schema(description = "장소 ID", example = "1")
	Long placeId,
	@Schema(description = "장소명", example = "부산시청")
	String name,
	@Schema(description = "서비스 장소 카테고리", example = "PUBLIC_OFFICE")
	PlaceCategory category,
	@Schema(description = "주소")
	String address,
	@Schema(description = "장소 좌표")
	GeoPointResponse point,
	@Schema(description = "외부 provider 장소 ID")
	String providerPlaceId,
	@Schema(description = "접근성 속성 목록")
	List<PlaceAccessibilityFeatureResponse> accessibilityFeatures) {

	public static AdminPlaceDetailResponse of(Place place, GeoPointConverter geoPointConverter) {
		return of(place, place.getAccessibilityFeatures(), geoPointConverter);
	}

	public static AdminPlaceDetailResponse of(
		Place place,
		List<PlaceAccessibilityFeature> accessibilityFeatures,
		GeoPointConverter geoPointConverter) {
		return new AdminPlaceDetailResponse(
			place.getPlaceId(),
			place.getName(),
			place.getCategory(),
			place.getAddress(),
			geoPointConverter.toResponse(place.getPoint()),
			place.getProviderPlaceId(),
			accessibilityFeatures
				.stream()
				.map(PlaceAccessibilityFeatureResponse::from)
				.sorted(Comparator.comparing(feature -> feature.featureType().name()))
				.toList());
	}
}
