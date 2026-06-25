package com.ssafy.e102.domain.route.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.ssafy.e102.domain.route.dto.request.WalkRouteSearchRequest;
import com.ssafy.e102.domain.route.dto.response.WalkRouteSearchResponse;
import com.ssafy.e102.domain.route.exception.RouteErrorCode;
import com.ssafy.e102.domain.route.exception.RouteException;
import com.ssafy.e102.domain.route.type.RouteOption;
import com.ssafy.e102.domain.route.type.TransportMode;
import com.ssafy.e102.domain.route.type.WalkRouteProfile;
import com.ssafy.e102.domain.user.type.MobilitySubtype;
import com.ssafy.e102.domain.user.type.PrimaryUserType;
import com.ssafy.e102.global.external.graphhopper.GraphHopperCoordinate;
import com.ssafy.e102.global.external.graphhopper.GraphHopperRoutePath;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

class WalkRouteSearchServiceTest {

	@Mock
	private WalkRouteUserProfileQueryService userProfileQueryService;

	@Mock
	private WalkRouteGraphHopperSearchService graphHopperSearchService;

	@Mock
	private RouteSearchCacheService routeSearchCacheService;

	private WalkRouteSearchService service;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		service = new WalkRouteSearchService(
			userProfileQueryService,
			graphHopperSearchService,
			new WalkRoutePayloadService(new RouteTurnInstructionService()),
			routeSearchCacheService);
	}

	@Test
	@DisplayName("토큰 사용자 ID로 사용자 유형을 조회해 도보 profile 후보 검색에 연결한다")
	void searchUsesTokenUserProfile() {
		UUID userId = UUID.randomUUID();
		GeoPointRequest startPoint = new GeoPointRequest(35.12, 128.936);
		GeoPointRequest endPoint = new GeoPointRequest(35.1315, 128.8823);
		when(userProfileQueryService.getProfile(userId))
			.thenReturn(new WalkRouteUserProfile(PrimaryUserType.MOBILITY_IMPAIRED, MobilitySubtype.POWER_WHEELCHAIR));
		when(graphHopperSearchService.searchCandidates(
			startPoint,
			endPoint,
			PrimaryUserType.MOBILITY_IMPAIRED,
			MobilitySubtype.POWER_WHEELCHAIR))
			.thenReturn(List.of(candidate(RouteOption.SAFE, WalkRouteProfile.WHEELCHAIR_AUTO_SAFE)));

		WalkRouteSearchResponse response = service.search(userId, new WalkRouteSearchRequest(startPoint, endPoint));

		assertThat(response.searchId()).startsWith("rs_walk_");
		assertThat(response.routes()).hasSize(1);
		assertThat(response.routes().get(0).transportMode()).isEqualTo(TransportMode.WALK);
		assertThat(response.routes().get(0).routeOption()).isEqualTo(RouteOption.SAFE);
		assertThat(response.routes().get(0).title()).isEqualTo("안전 경로");
		assertThat(response.routes().get(0).distanceMeter()).isEqualByComparingTo("120.50");
		assertThat(response.routes().get(0).durationSecond()).isEqualTo(90);
		assertThat(response.routes().get(0).estimatedTimeMinute()).isEqualTo(1);
		assertThat(response.routes().get(0).geometry())
			.isEqualTo("LINESTRING(128.936 35.12, 128.8823 35.1315)");
		assertThat(response.routes().get(0).legs().get(0).guidanceEvents()).isEmpty();
		verify(routeSearchCacheService).save(userId, response);
		verify(graphHopperSearchService).searchCandidates(
			startPoint,
			endPoint,
			PrimaryUserType.MOBILITY_IMPAIRED,
			MobilitySubtype.POWER_WHEELCHAIR);
	}

	@Test
	@DisplayName("부산 서비스 영역 밖 좌표는 GraphHopper 호출 전에 RT4003으로 차단한다")
	void rejectOutOfServiceAreaBeforeGraphHopper() {
		UUID userId = UUID.randomUUID();
		when(userProfileQueryService.getProfile(userId))
			.thenReturn(new WalkRouteUserProfile(PrimaryUserType.LOW_VISION, null));

		assertThatThrownBy(() -> service.search(userId, new WalkRouteSearchRequest(
			new GeoPointRequest(37.5665, 126.9780),
			new GeoPointRequest(35.1315, 128.8823))))
			.isInstanceOf(RouteException.class)
			.extracting(exception -> ((RouteException)exception).getErrorCode())
			.isEqualTo(RouteErrorCode.OUT_OF_SERVICE_AREA);
		verify(graphHopperSearchService, never()).searchCandidates(
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any());
	}

	@Test
	@DisplayName("출발지와 도착지가 20m 이하면 RT4004로 차단한다")
	void rejectTooCloseStartAndEnd() {
		UUID userId = UUID.randomUUID();
		when(userProfileQueryService.getProfile(userId))
			.thenReturn(new WalkRouteUserProfile(PrimaryUserType.LOW_VISION, null));

		assertThatThrownBy(() -> service.search(userId, new WalkRouteSearchRequest(
			new GeoPointRequest(35.120000, 128.936000),
			new GeoPointRequest(35.120010, 128.936010))))
			.isInstanceOf(RouteException.class)
			.extracting(exception -> ((RouteException)exception).getErrorCode())
			.isEqualTo(RouteErrorCode.START_END_TOO_CLOSE);
		verify(graphHopperSearchService, never()).searchCandidates(
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any());
	}

	private WalkRouteCandidate candidate(RouteOption routeOption, WalkRouteProfile profile) {
		return new WalkRouteCandidate(
			routeOption,
			profile,
			new GraphHopperRoutePath(
				new BigDecimal("120.50"),
				90_000,
				List.of(
					new GraphHopperCoordinate(new BigDecimal("128.936"), new BigDecimal("35.12")),
					new GraphHopperCoordinate(new BigDecimal("128.8823"), new BigDecimal("35.1315"))),
				Map.of()));
	}

}
