package com.ssafy.e102.domain.place.dto.response;

import java.util.List;
import java.util.Set;

import com.ssafy.e102.domain.place.entity.Place;
import com.ssafy.e102.domain.place.type.PlaceCategory;
import com.ssafy.e102.domain.place.type.PlaceMarkerKind;
import com.ssafy.e102.global.geo.GeoPointConverter;
import com.ssafy.e102.global.geo.dto.GeoPointResponse;

public record PlaceMarkerResponse(
	Long placeId,
	String name,
	PlaceCategory category,
	String address,
	GeoPointResponse point,
	List<PlaceAccessibilityFeatureResponse> accessibilityFeatures,
	boolean isBookmarked,
	PlaceMarkerKind markerKind) {

	public static PlaceMarkerResponse of(Place place, Set<Long> bookmarkedPlaceIds,
		GeoPointConverter geoPointConverter, PlaceMarkerKind markerKind) {
		return new PlaceMarkerResponse(
			place.getPlaceId(),
			place.getName(),
			place.getCategory(),
			place.getAddress(),
			geoPointConverter.toResponse(place.getPoint()),
			place.getAccessibilityFeatures()
				.stream()
				.map(PlaceAccessibilityFeatureResponse::from)
				.toList(),
			bookmarkedPlaceIds.contains(place.getPlaceId()),
			markerKind);
	}
}
