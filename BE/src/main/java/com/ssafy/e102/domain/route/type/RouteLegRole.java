package com.ssafy.e102.domain.route.type;

/**
 * route leg가 전체 route 안에서 어떤 역할을 하는지 나타낸다.
 *
 * <p>현재 도보 검색은 WALK_ONLY만 사용하고, 대중교통 구현에서 승차/환승 역할이 추가될 수 있다.
 */
public enum RouteLegRole {
	WALK_ONLY,
	WALK_TO_TRANSIT,
	TRANSIT,
	TRANSIT_TO_WALK,
	WALK_TO_DESTINATION
}
