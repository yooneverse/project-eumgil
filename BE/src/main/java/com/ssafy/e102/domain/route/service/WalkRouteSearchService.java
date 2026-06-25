package com.ssafy.e102.domain.route.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ssafy.e102.domain.route.dto.request.WalkRouteSearchRequest;
import com.ssafy.e102.domain.route.dto.response.RouteSummaryResponse;
import com.ssafy.e102.domain.route.dto.response.WalkRouteSearchResponse;
import com.ssafy.e102.domain.route.exception.RouteErrorCode;
import com.ssafy.e102.domain.route.exception.RouteException;
import com.ssafy.e102.global.geo.GeoDistanceCalculator;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

/**
 * `POST /routes/search/walk`의 상위 흐름을 담당한다.
 *
 * <p>Controller에서 인증 사용자 ID와 start/end 좌표를 받으면 user table에서 접근성 profile 기준을 조회하고,
 * 부산 서비스 영역/20m 근접 검증을 먼저 수행한 뒤 {@link WalkRouteGraphHopperSearchService}에 후보 조회를 맡긴다.
 */
@Service
public class WalkRouteSearchService {

	private static final double BUSAN_MIN_LAT = 34.85;
	private static final double BUSAN_MAX_LAT = 35.45;
	private static final double BUSAN_MIN_LNG = 128.70;
	private static final double BUSAN_MAX_LNG = 129.40;
	private static final double START_END_MIN_DISTANCE_METER = 20.0;

	private final WalkRouteUserProfileQueryService userProfileQueryService;
	private final WalkRouteGraphHopperSearchService graphHopperSearchService;
	private final WalkRoutePayloadService walkRoutePayloadService;
	private final RouteSearchCacheService routeSearchCacheService;

	public WalkRouteSearchService(
		WalkRouteUserProfileQueryService userProfileQueryService,
		WalkRouteGraphHopperSearchService graphHopperSearchService,
		WalkRoutePayloadService walkRoutePayloadService,
		RouteSearchCacheService routeSearchCacheService) {
		this.userProfileQueryService = userProfileQueryService;
		this.graphHopperSearchService = graphHopperSearchService;
		this.walkRoutePayloadService = walkRoutePayloadService;
		this.routeSearchCacheService = routeSearchCacheService;
	}

	public WalkRouteSearchResponse search(UUID userId, WalkRouteSearchRequest request) {
		WalkRouteUserProfile profile = userProfileQueryService.getProfile(userId);
		GeoPointRequest startPoint = request.startPoint();
		GeoPointRequest endPoint = request.endPoint();
		validateServiceArea(startPoint, endPoint);
		validateStartEndDistance(startPoint, endPoint);

		List<WalkRouteCandidate> candidates = graphHopperSearchService.searchCandidates(
			startPoint,
			endPoint,
			profile.primaryUserType(),
			profile.mobilitySubtype());
		String searchId = "rs_walk_" + UUID.randomUUID();
		WalkRouteSearchResponse response = new WalkRouteSearchResponse(searchId,
			toRouteSummaries(searchId, candidates));
		routeSearchCacheService.save(userId, response);
		return response;
	}

	private void validateServiceArea(GeoPointRequest startPoint, GeoPointRequest endPoint) {
		if (!isInBusan(startPoint) || !isInBusan(endPoint)) {
			throw new RouteException(RouteErrorCode.OUT_OF_SERVICE_AREA);
		}
	}

	private boolean isInBusan(GeoPointRequest point) {
		return point.lat() >= BUSAN_MIN_LAT
			&& point.lat() <= BUSAN_MAX_LAT
			&& point.lng() >= BUSAN_MIN_LNG
			&& point.lng() <= BUSAN_MAX_LNG;
	}

	private void validateStartEndDistance(GeoPointRequest startPoint, GeoPointRequest endPoint) {
		if (GeoDistanceCalculator.distanceMeter(startPoint, endPoint) <= START_END_MIN_DISTANCE_METER) {
			throw new RouteException(RouteErrorCode.START_END_TOO_CLOSE);
		}
	}

	private List<RouteSummaryResponse> toRouteSummaries(String searchId, List<WalkRouteCandidate> candidates) {
		return candidates.stream()
			.map(candidate -> walkRoutePayloadService.toRouteSummary(searchId, candidate))
			.toList();
	}
}
