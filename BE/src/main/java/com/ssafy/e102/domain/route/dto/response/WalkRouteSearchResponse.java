package com.ssafy.e102.domain.route.dto.response;

import java.util.List;

/**
 * 도보 경로 검색 결과의 최상위 응답 DTO다.
 *
 * <p>route search service가 만든 searchId와 후보 route 목록만 담고, Redis 저장용 내부 metadata는 노출하지 않는다.
 */
public record WalkRouteSearchResponse(
	String searchId,
	List<RouteSummaryResponse> routes) {
}
