package com.ssafy.e102.domain.place.dto.response;

import java.util.List;
import java.util.Map;

import com.ssafy.e102.domain.place.entity.Place;
import com.ssafy.e102.domain.place.type.PlaceCategory;
import com.ssafy.e102.global.external.kakao.KakaoPlaceDocument;
import com.ssafy.e102.global.geo.dto.GeoPointResponse;

public record PlaceSearchItemResponse(
	Long placeId,
	String provider,
	String providerPlaceId,
	String name,
	PlaceCategory category,
	String address,
	Integer distanceMeter,
	GeoPointResponse point,
	List<PlaceAccessibilityFeatureResponse> accessibilityFeatures,
	boolean matched) {

	public static PlaceSearchItemResponse of(
		KakaoPlaceDocument kakaoPlace,
		Map<String, Place> matchedPlaces) {
		Place matchedPlace = matchedPlaces.get(kakaoPlace.id());
		boolean matched = matchedPlace != null;
		return new PlaceSearchItemResponse(
			matched ? matchedPlace.getPlaceId() : null,
			"KAKAO",
			kakaoPlace.id(),
			kakaoPlace.placeName(),
			matched ? matchedPlace.getCategory() : null,
			kakaoPlace.address(),
			kakaoPlace.distanceMeter(),
			kakaoPlace.point(),
			matched ? matchedPlace.getAccessibilityFeatures()
				.stream()
				.map(PlaceAccessibilityFeatureResponse::from)
				.toList() : List.of(),
			matched);
	}
}
