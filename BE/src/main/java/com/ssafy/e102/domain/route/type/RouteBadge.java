package com.ssafy.e102.domain.route.type;

/**
 * route/step 응답에서 접근성 특성을 짧은 라벨로 노출할 때 쓰는 값이다.
 *
 * <p>segment_features와 road_segments 해석 결과가 payload 매핑 service에서 이 enum으로 변환된다.
 */
public enum RouteBadge {
	LOW_SLOPE,
	MIDDLE_SLOPE,
	STAIR,
	CROSSWALK,
	ELEVATOR,
	NARROW_SIDEWALK,
	UNPAVED
}
