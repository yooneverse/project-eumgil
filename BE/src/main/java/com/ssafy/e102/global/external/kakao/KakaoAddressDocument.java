package com.ssafy.e102.global.external.kakao;

import org.springframework.util.StringUtils;

public record KakaoAddressDocument(
	String address,
	String roadAddress,
	String buildingName,
	String region1DepthName,
	String region2DepthName,
	String region3DepthName) {

	public String displayAddress() {
		if (StringUtils.hasText(roadAddress)) {
			return roadAddress;
		}
		return address;
	}
}
