package com.ssafy.e102.domain.route.type;

/**
 * 사용자가 선택하거나 backend가 생성하는 route 후보 유형이다.
 *
 * <p>도보 검색 service는 SAFE와 SHORTEST를 모두 GraphHopper profile로 변환해 후보를 만든다.
 */
public enum RouteOption {
	SAFE,
	SHORTEST,
	RECOMMENDED,
	MIN_TRANSFER,
	MIN_WALK
}
