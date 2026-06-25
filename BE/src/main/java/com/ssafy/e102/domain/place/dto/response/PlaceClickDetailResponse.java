package com.ssafy.e102.domain.place.dto.response;

import java.util.List;

import com.ssafy.e102.domain.place.type.PlaceCategory;
import com.ssafy.e102.domain.place.type.PlaceDetailType;
import com.ssafy.e102.global.geo.dto.GeoPointResponse;

public record PlaceClickDetailResponse(
	String bookmarkTargetId,
	PlaceDetailType detailType,
	Long placeId,
	String provider,
	String providerPlaceId,
	String name,
	PlaceCategory category,
	String providerCategory,
	String phone,
	String address,
	GeoPointResponse point,
	List<PlaceAccessibilityFeatureResponse> accessibilityFeatures,
	List<PlaceTransitArrivalResponse> transitArrivals,
	boolean isBookmarked) {

	public PlaceClickDetailResponse(
		String bookmarkTargetId,
		PlaceDetailType detailType,
		Long placeId,
		String provider,
		String providerPlaceId,
		String name,
		PlaceCategory category,
		String providerCategory,
		String phone,
		String address,
		GeoPointResponse point,
		List<PlaceAccessibilityFeatureResponse> accessibilityFeatures,
		boolean isBookmarked) {
		this(
			bookmarkTargetId,
			detailType,
			placeId,
			provider,
			providerPlaceId,
			name,
			category,
			providerCategory,
			phone,
			address,
			point,
			accessibilityFeatures,
			List.of(),
			isBookmarked);
	}
}
