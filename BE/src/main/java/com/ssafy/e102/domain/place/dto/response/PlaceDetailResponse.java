package com.ssafy.e102.domain.place.dto.response;

import java.util.List;

import com.ssafy.e102.domain.place.entity.Place;
import com.ssafy.e102.domain.place.type.PlaceCategory;
import com.ssafy.e102.global.geo.GeoPointConverter;
import com.ssafy.e102.global.geo.dto.GeoPointResponse;

public record PlaceDetailResponse(
	Long placeId,
	String name,
	PlaceCategory category,
	String address,
	GeoPointResponse point,
	String providerPlaceId,
	List<PlaceAccessibilityFeatureResponse> accessibilityFeatures,
	boolean isBookmarked) {

	public static PlaceDetailResponse of(Place place, boolean isBookmarked, GeoPointConverter geoPointConverter) {
		return new PlaceDetailResponse(
			place.getPlaceId(),
			place.getName(),
			place.getCategory(),
			place.getAddress(),
			geoPointConverter.toResponse(place.getPoint()),
			place.getProviderPlaceId(),
			place.getAccessibilityFeatures()
				.stream()
				.map(PlaceAccessibilityFeatureResponse::from)
				.toList(),
			isBookmarked);
	}
}
