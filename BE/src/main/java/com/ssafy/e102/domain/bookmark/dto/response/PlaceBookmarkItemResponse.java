package com.ssafy.e102.domain.bookmark.dto.response;

import java.util.List;

import com.ssafy.e102.domain.place.dto.response.PlaceAccessibilityFeatureResponse;
import com.ssafy.e102.domain.place.type.PlaceCategory;
import com.ssafy.e102.domain.place.type.PlaceDetailType;
import com.ssafy.e102.global.geo.dto.GeoPointResponse;

public record PlaceBookmarkItemResponse(
	Long bookmarkId,
	String bookmarkTargetId,
	PlaceDetailType targetType,
	Long placeId,
	String provider,
	String providerPlaceId,
	String name,
	PlaceCategory category,
	String providerCategory,
	String address,
	GeoPointResponse point,
	List<PlaceAccessibilityFeatureResponse> accessibilityFeatures) {
}
