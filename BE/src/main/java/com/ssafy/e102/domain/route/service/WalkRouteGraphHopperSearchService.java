package com.ssafy.e102.domain.route.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ssafy.e102.domain.route.exception.RouteErrorCode;
import com.ssafy.e102.domain.route.exception.RouteException;
import com.ssafy.e102.domain.route.type.RouteOption;
import com.ssafy.e102.domain.route.type.WalkRouteProfile;
import com.ssafy.e102.domain.user.type.MobilitySubtype;
import com.ssafy.e102.domain.user.type.PrimaryUserType;
import com.ssafy.e102.global.external.graphhopper.GraphHopperRouteClient;
import com.ssafy.e102.global.external.graphhopper.GraphHopperRoutePath;
import com.ssafy.e102.global.external.graphhopper.GraphHopperRouteRequest;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

/**
 * 도보 검색 요청을 GraphHopper 후보 조회 흐름으로 연결한다.
 *
 * <p>상위 route search service가 출발지/도착지와 사용자 유형을 넘기면,
 * {@link WalkRouteProfileService}로 SAFE/SHORTEST profile을 정하고
 * {@link GraphHopperRouteClient}에 실제 외부 경로 조회를 맡긴다.
 */
@Service
public class WalkRouteGraphHopperSearchService {

	private final WalkRouteProfileService profileService;
	private final GraphHopperRouteClient graphHopperRouteClient;

	public WalkRouteGraphHopperSearchService(
		WalkRouteProfileService profileService,
		GraphHopperRouteClient graphHopperRouteClient) {
		this.profileService = profileService;
		this.graphHopperRouteClient = graphHopperRouteClient;
	}

	public List<WalkRouteCandidate> searchCandidates(
		GeoPointRequest startPoint,
		GeoPointRequest endPoint,
		PrimaryUserType primaryUserType,
		MobilitySubtype mobilitySubtype) {
		List<WalkRouteCandidate> candidates = new ArrayList<>();
		// 도보 검색은 항상 SAFE와 SHORTEST를 같은 순서로 조회해 응답 후보 순서를 고정한다.
		for (RouteOption routeOption : List.of(RouteOption.SAFE, RouteOption.SHORTEST)) {
			findCandidate(startPoint, endPoint, primaryUserType, mobilitySubtype, routeOption, candidates);
		}
		if (candidates.isEmpty()) {
			throw new RouteException(RouteErrorCode.ROUTE_NOT_FOUND);
		}
		return List.copyOf(candidates);
	}

	private void findCandidate(
		GeoPointRequest startPoint,
		GeoPointRequest endPoint,
		PrimaryUserType primaryUserType,
		MobilitySubtype mobilitySubtype,
		RouteOption routeOption,
		List<WalkRouteCandidate> candidates) {
		WalkRouteProfile profile = profileService.resolve(primaryUserType, mobilitySubtype, routeOption);
		try {
			// 외부 응답 원본은 client 내부 DTO로 닫고, route service에는 정제된 path만 넘긴다.
			GraphHopperRoutePath path = graphHopperRouteClient.route(
				new GraphHopperRouteRequest(startPoint, endPoint, profile, false));
			candidates.add(new WalkRouteCandidate(routeOption, profile, path));
		} catch (RouteException exception) {
			// 한 option만 경로가 없을 수 있으므로 RT4040은 후보 제외로 다루고, 둘 다 없을 때만 실패한다.
			if (exception.getErrorCode() == RouteErrorCode.ROUTE_NOT_FOUND) {
				return;
			}
			throw exception;
		}
	}
}
