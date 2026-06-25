package com.ssafy.e102.global.external.kakao;

import com.ssafy.e102.global.geo.dto.GeoPointResponse;

public record KakaoPlaceDocument(
	String id,
	String placeName,
	String address,
	String providerCategory,
	String phone,
	Integer distanceMeter,
	GeoPointResponse point) {
}
