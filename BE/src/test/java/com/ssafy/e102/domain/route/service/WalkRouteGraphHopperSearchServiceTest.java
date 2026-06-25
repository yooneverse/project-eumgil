package com.ssafy.e102.domain.route.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.ssafy.e102.domain.route.exception.RouteErrorCode;
import com.ssafy.e102.domain.route.exception.RouteException;
import com.ssafy.e102.domain.route.type.RouteOption;
import com.ssafy.e102.domain.route.type.WalkRouteProfile;
import com.ssafy.e102.domain.user.type.MobilitySubtype;
import com.ssafy.e102.domain.user.type.PrimaryUserType;
import com.ssafy.e102.global.external.graphhopper.GraphHopperCoordinate;
import com.ssafy.e102.global.external.graphhopper.GraphHopperRouteClient;
import com.ssafy.e102.global.external.graphhopper.GraphHopperRoutePath;
import com.ssafy.e102.global.external.graphhopper.GraphHopperRouteRequest;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

class WalkRouteGraphHopperSearchServiceTest {

	private final WalkRouteProfileService profileService = new WalkRouteProfileService();
	private final GraphHopperRouteClient graphHopperRouteClient = Mockito.mock(GraphHopperRouteClient.class);
	private final WalkRouteGraphHopperSearchService service = new WalkRouteGraphHopperSearchService(
		profileService,
		graphHopperRouteClient);

	@Test
	@DisplayName("SAFE와 SHORTEST GraphHopper 후보를 사용자 profile 기준으로 조회한다")
	void searchCandidatesRequestsSafeAndShortestProfiles() {
		when(graphHopperRouteClient.route(any(GraphHopperRouteRequest.class)))
			.thenReturn(path("100.0"), path("95.0"));

		List<WalkRouteCandidate> candidates = service.searchCandidates(
			new GeoPointRequest(35.12, 128.936),
			new GeoPointRequest(35.1315, 128.8823),
			PrimaryUserType.MOBILITY_IMPAIRED,
			MobilitySubtype.POWER_WHEELCHAIR);

		assertThat(candidates)
			.extracting(WalkRouteCandidate::routeOption)
			.containsExactly(RouteOption.SAFE, RouteOption.SHORTEST);
		assertThat(candidates)
			.extracting(WalkRouteCandidate::profile)
			.containsExactly(WalkRouteProfile.WHEELCHAIR_AUTO_SAFE, WalkRouteProfile.WHEELCHAIR_AUTO_FAST);

		ArgumentCaptor<GraphHopperRouteRequest> captor = ArgumentCaptor.forClass(GraphHopperRouteRequest.class);
		Mockito.verify(graphHopperRouteClient, Mockito.times(2)).route(captor.capture());
		assertThat(captor.getAllValues())
			.extracting(GraphHopperRouteRequest::profile)
			.containsExactly(WalkRouteProfile.WHEELCHAIR_AUTO_SAFE, WalkRouteProfile.WHEELCHAIR_AUTO_FAST);
		assertThat(captor.getAllValues())
			.extracting(GraphHopperRouteRequest::enforceSnapDistanceLimit)
			.containsExactly(false, false);
	}

	@Test
	@DisplayName("일부 profile 후보 없음은 건너뛰고 남은 후보를 반환한다")
	void searchCandidatesSkipsNotFoundCandidate() {
		when(graphHopperRouteClient.route(any(GraphHopperRouteRequest.class)))
			.thenThrow(new RouteException(RouteErrorCode.ROUTE_NOT_FOUND))
			.thenReturn(path("95.0"));

		List<WalkRouteCandidate> candidates = service.searchCandidates(
			new GeoPointRequest(35.12, 128.936),
			new GeoPointRequest(35.1315, 128.8823),
			PrimaryUserType.LOW_VISION,
			null);

		assertThat(candidates).hasSize(1);
		assertThat(candidates.get(0).routeOption()).isEqualTo(RouteOption.SHORTEST);
		assertThat(candidates.get(0).profile()).isEqualTo(WalkRouteProfile.VISUAL_FAST);
	}

	@Test
	@DisplayName("모든 GraphHopper 후보가 없으면 RT4040을 반환한다")
	void searchCandidatesThrowsRouteNotFoundWhenAllCandidatesAreEmpty() {
		when(graphHopperRouteClient.route(any(GraphHopperRouteRequest.class)))
			.thenThrow(new RouteException(RouteErrorCode.ROUTE_NOT_FOUND));

		assertThatThrownBy(() -> service.searchCandidates(
			new GeoPointRequest(35.12, 128.936),
			new GeoPointRequest(35.1315, 128.8823),
			PrimaryUserType.LOW_VISION,
			null))
			.isInstanceOf(RouteException.class)
			.extracting(exception -> ((RouteException)exception).getErrorCode())
			.isEqualTo(RouteErrorCode.ROUTE_NOT_FOUND);
	}

	@Test
	@DisplayName("GraphHopper 실패는 즉시 전파한다")
	void searchCandidatesPropagatesGraphHopperFailure() {
		when(graphHopperRouteClient.route(any(GraphHopperRouteRequest.class)))
			.thenThrow(new RouteException(RouteErrorCode.EXTERNAL_ROUTE_API_FAILED));

		assertThatThrownBy(() -> service.searchCandidates(
			new GeoPointRequest(35.12, 128.936),
			new GeoPointRequest(35.1315, 128.8823),
			PrimaryUserType.LOW_VISION,
			null))
			.isInstanceOf(RouteException.class)
			.extracting(exception -> ((RouteException)exception).getErrorCode())
			.isEqualTo(RouteErrorCode.EXTERNAL_ROUTE_API_FAILED);
	}

	private GraphHopperRoutePath path(String distanceMeter) {
		return new GraphHopperRoutePath(
			new BigDecimal(distanceMeter),
			60000,
			List.of(new GraphHopperCoordinate(new BigDecimal("128.936"), new BigDecimal("35.12"))),
			Map.of());
	}
}
