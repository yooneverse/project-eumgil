package com.ssafy.e102.global.external.kakao;

import java.util.List;

public record KakaoPlaceSearchResult(
	List<KakaoPlaceDocument> documents,
	long totalElements,
	boolean isEnd) {
}
