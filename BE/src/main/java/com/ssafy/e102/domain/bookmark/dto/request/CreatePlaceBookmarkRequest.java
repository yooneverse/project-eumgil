package com.ssafy.e102.domain.bookmark.dto.request;

import com.ssafy.e102.global.geo.dto.GeoPointRequest;

import jakarta.validation.Valid;

public record CreatePlaceBookmarkRequest(
	Long placeId,
	String provider,
	String providerPlaceId,
	String name,
	String providerCategory,
	String address,
	@Valid
	GeoPointRequest point) {
}
