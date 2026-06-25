package com.ssafy.e102.domain.place.dto.response;

import com.ssafy.e102.global.external.kakao.KakaoAddressDocument;

public record PlaceReverseGeocodeResponse(
	String displayAddress,
	String roadAddress,
	String address,
	String region1DepthName,
	String region2DepthName,
	String region3DepthName) {

	public static PlaceReverseGeocodeResponse from(KakaoAddressDocument document) {
		return new PlaceReverseGeocodeResponse(
			document.displayAddress(),
			document.roadAddress(),
			document.address(),
			document.region1DepthName(),
			document.region2DepthName(),
			document.region3DepthName());
	}
}
