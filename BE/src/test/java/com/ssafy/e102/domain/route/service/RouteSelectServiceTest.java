package com.ssafy.e102.domain.route.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.locationtech.jts.geom.Point;
import org.springframework.dao.DataIntegrityViolationException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.e102.domain.route.dto.request.SelectRouteRequest;
import com.ssafy.e102.domain.route.dto.response.RouteLegResponse;
import com.ssafy.e102.domain.route.dto.response.RouteSelectResponse;
import com.ssafy.e102.domain.route.dto.response.RouteSessionResponse;
import com.ssafy.e102.domain.route.dto.response.RouteStopResponse;
import com.ssafy.e102.domain.route.dto.response.RouteSummaryResponse;
import com.ssafy.e102.domain.route.dto.response.TransitLaneOptionResponse;
import com.ssafy.e102.domain.route.exception.RouteErrorCode;
import com.ssafy.e102.domain.route.exception.RouteException;
import com.ssafy.e102.domain.route.type.RouteBadge;
import com.ssafy.e102.domain.route.type.RouteLegRole;
import com.ssafy.e102.domain.route.type.RouteOption;
import com.ssafy.e102.domain.route.type.TransportMode;

class RouteSelectServiceTest {

	private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

	private RouteSearchCacheService routeSearchCacheService;
	private RouteSessionCommandService routeSessionCommandService;
	private RouteSelectService service;

	@BeforeEach
	void setUp() {
		routeSearchCacheService = mock(RouteSearchCacheService.class);
		routeSessionCommandService = mock(RouteSessionCommandService.class);
		service = new RouteSelectService(routeSearchCacheService, routeSessionCommandService, new ObjectMapper());
		when(routeSessionCommandService.saveActiveSessionIfAbsent(
			org.mockito.ArgumentMatchers.any(UUID.class),
			org.mockito.ArgumentMatchers.any(String.class),
			org.mockito.ArgumentMatchers.any(Point.class),
			org.mockito.ArgumentMatchers.any(Point.class),
			org.mockito.ArgumentMatchers.any(JsonNode.class)))
			.thenReturn(new RouteSessionResponse(UUID.fromString("00000000-0000-0000-0000-000000000099")));
		when(routeSessionCommandService.getActiveSession(
			org.mockito.ArgumentMatchers.any(UUID.class),
			org.mockito.ArgumentMatchers.any(String.class)))
			.thenReturn(new RouteSessionResponse(UUID.fromString("00000000-0000-0000-0000-000000000099")));
	}

	@Test
	@DisplayName("Redis 후보 route를 ACTIVE route session으로 저장한다")
	void selectStoresActiveRouteSession() {
		RouteSummaryResponse route = transitRoute("rt_selected_001");
		when(routeSearchCacheService.getOwnedRouteOrThrow(USER_ID, "rs_transit_test", "rt_selected_001"))
			.thenReturn(route);

		service.select(USER_ID, "rt_selected_001", new SelectRouteRequest("rs_transit_test"));

		ArgumentCaptor<Point> startPointCaptor = ArgumentCaptor.forClass(Point.class);
		ArgumentCaptor<Point> endPointCaptor = ArgumentCaptor.forClass(Point.class);
		ArgumentCaptor<JsonNode> snapshotCaptor = ArgumentCaptor.forClass(JsonNode.class);
		verify(routeSessionCommandService).saveActiveSessionIfAbsent(
			org.mockito.ArgumentMatchers.eq(USER_ID),
			org.mockito.ArgumentMatchers.eq("rt_selected_001"),
			startPointCaptor.capture(),
			endPointCaptor.capture(),
			snapshotCaptor.capture());
		assertThat(startPointCaptor.getValue().getY()).isEqualTo(35.12);
		assertThat(startPointCaptor.getValue().getX()).isEqualTo(128.936);
		assertThat(endPointCaptor.getValue().getY()).isEqualTo(35.14);
		assertThat(endPointCaptor.getValue().getX()).isEqualTo(128.956);
		assertThat(snapshotCaptor.getValue().get("routeId").asText()).isEqualTo("rt_selected_001");
	}

	@Test
	@DisplayName("select response exposes total route metrics without using remaining field names")
	void selectResponseExposesTotalRouteMetrics() {
		UUID sessionId = UUID.fromString("00000000-0000-0000-0000-000000000099");
		RouteSummaryResponse route = transitRoute("rt_selected_001");
		when(routeSearchCacheService.getOwnedRouteOrThrow(USER_ID, "rs_transit_test", "rt_selected_001"))
			.thenReturn(route);
		when(routeSessionCommandService.saveActiveSessionIfAbsent(
			org.mockito.ArgumentMatchers.eq(USER_ID),
			org.mockito.ArgumentMatchers.eq("rt_selected_001"),
			org.mockito.ArgumentMatchers.any(Point.class),
			org.mockito.ArgumentMatchers.any(Point.class),
			org.mockito.ArgumentMatchers.any(JsonNode.class)))
			.thenReturn(new RouteSessionResponse(sessionId));

		RouteSelectResponse response = service.select(USER_ID, "rt_selected_001",
			new SelectRouteRequest("rs_transit_test"));

		assertThat(response.sessionId()).isEqualTo(sessionId);
		assertThat(response.totalDistanceMeter()).isEqualByComparingTo(BigDecimal.valueOf(2500));
		assertThat(response.totalDurationSecond()).isEqualTo(900);
		assertThat(Arrays.stream(RouteSelectResponse.class.getRecordComponents())
			.map(component -> component.getName())
			.toList())
			.containsExactly("sessionId", "totalDistanceMeter", "totalDurationSecond");
	}

	@Test
	@DisplayName("선택 snapshot에는 검색 시점 실시간 remainingMinute를 저장하지 않는다")
	void selectRemovesLiveRemainingMinuteFromSnapshot() {
		when(routeSearchCacheService.getOwnedRouteOrThrow(USER_ID, "rs_transit_test", "rt_selected_001"))
			.thenReturn(transitRoute("rt_selected_001"));

		service.select(USER_ID, "rt_selected_001", new SelectRouteRequest("rs_transit_test"));

		ArgumentCaptor<JsonNode> snapshotCaptor = ArgumentCaptor.forClass(JsonNode.class);
		verify(routeSessionCommandService).saveActiveSessionIfAbsent(
			org.mockito.ArgumentMatchers.eq(USER_ID),
			org.mockito.ArgumentMatchers.eq("rt_selected_001"),
			org.mockito.ArgumentMatchers.any(Point.class),
			org.mockito.ArgumentMatchers.any(Point.class),
			snapshotCaptor.capture());
		JsonNode laneOption = snapshotCaptor.getValue()
			.get("legs")
			.get(0)
			.get("laneOptions")
			.get(0);
		assertThat(laneOption.has("remainingMinute")).isFalse();
		assertThat(laneOption.get("routeNo").asText()).isEqualTo("강서구13");
	}

	@Test
	@DisplayName("대중교통 metadata가 있으면 route snapshot에 backendMetadata로 함께 저장한다")
	void selectStoresTransitBackendMetadata() {
		when(routeSearchCacheService.getOwnedRouteOrThrow(USER_ID, "rs_transit_test", "rt_selected_001"))
			.thenReturn(transitRoute("rt_selected_001"));
		when(routeSearchCacheService.findTransitMetadata("rs_transit_test", "rt_selected_001"))
			.thenReturn(java.util.Optional.of(new TransitRouteSnapshot(
				"rt_selected_001",
				"map-object",
				List.of(Map.of(
					"type", TransportMode.BUS,
					"lanes", List.of(Map.of("busLocalBlID", "BL1")),
					"passStops", List.of(Map.of("localStationID", "BS1")))))));

		service.select(USER_ID, "rt_selected_001", new SelectRouteRequest("rs_transit_test"));

		ArgumentCaptor<JsonNode> snapshotCaptor = ArgumentCaptor.forClass(JsonNode.class);
		verify(routeSessionCommandService).saveActiveSessionIfAbsent(
			org.mockito.ArgumentMatchers.eq(USER_ID),
			org.mockito.ArgumentMatchers.eq("rt_selected_001"),
			org.mockito.ArgumentMatchers.any(Point.class),
			org.mockito.ArgumentMatchers.any(Point.class),
			snapshotCaptor.capture());
		JsonNode backendMetadata = snapshotCaptor.getValue().get("backendMetadata");
		assertThat(backendMetadata.get("routeId").asText()).isEqualTo("rt_selected_001");
		assertThat(backendMetadata.get("mapObj").asText()).isEqualTo("map-object");
		assertThat(backendMetadata.get("legs").get(0).get("lanes").get(0).get("busLocalBlID").asText())
			.isEqualTo("BL1");
	}

	@Test
	@DisplayName("route geometry가 없으면 leg geometry의 처음과 끝을 session 좌표로 사용한다")
	void selectFallsBackToLegGeometryWhenRouteGeometryIsMissing() {
		RouteSummaryResponse route = new RouteSummaryResponse(
			"rt_selected_001",
			TransportMode.WALK,
			RouteOption.SAFE,
			"안전 경로",
			BigDecimal.valueOf(100),
			60,
			1,
			List.of(RouteBadge.CROSSWALK),
			null,
			List.of(walkLeg("LINESTRING(128.936 35.12, 128.956 35.14)")));
		when(routeSearchCacheService.getOwnedRouteOrThrow(USER_ID, "rs_walk_test", "rt_selected_001"))
			.thenReturn(route);

		service.select(USER_ID, "rt_selected_001", new SelectRouteRequest("rs_walk_test"));

		ArgumentCaptor<Point> startPointCaptor = ArgumentCaptor.forClass(Point.class);
		ArgumentCaptor<Point> endPointCaptor = ArgumentCaptor.forClass(Point.class);
		verify(routeSessionCommandService).saveActiveSessionIfAbsent(
			org.mockito.ArgumentMatchers.eq(USER_ID),
			org.mockito.ArgumentMatchers.eq("rt_selected_001"),
			startPointCaptor.capture(),
			endPointCaptor.capture(),
			org.mockito.ArgumentMatchers.any(JsonNode.class));
		assertThat(startPointCaptor.getValue().getY()).isEqualTo(35.12);
		assertThat(endPointCaptor.getValue().getY()).isEqualTo(35.14);
	}

	@Test
	@DisplayName("선택 route geometry를 해석할 수 없으면 RT4090으로 차단한다")
	void selectRejectsBrokenRouteGeometry() {
		RouteSummaryResponse route = new RouteSummaryResponse(
			"rt_selected_001",
			TransportMode.WALK,
			RouteOption.SAFE,
			"안전 경로",
			BigDecimal.valueOf(100),
			60,
			1,
			List.of(),
			"BROKEN",
			List.of());
		when(routeSearchCacheService.getOwnedRouteOrThrow(USER_ID, "rs_walk_test", "rt_selected_001"))
			.thenReturn(route);

		assertThatThrownBy(() -> service.select(USER_ID, "rt_selected_001", new SelectRouteRequest("rs_walk_test")))
			.isInstanceOf(RouteException.class)
			.extracting(exception -> ((RouteException)exception).getErrorCode())
			.isEqualTo(RouteErrorCode.ROUTE_SELECT_CONFLICT);
	}

	@Test
	@DisplayName("Redis 검색 결과가 만료되면 RT4041을 그대로 반환한다")
	void selectPropagatesExpiredSearchError() {
		when(routeSearchCacheService.getOwnedRouteOrThrow(USER_ID, "expired", "rt_selected_001"))
			.thenThrow(new RouteException(RouteErrorCode.ROUTE_SEARCH_EXPIRED));

		assertThatThrownBy(() -> service.select(USER_ID, "rt_selected_001", new SelectRouteRequest("expired")))
			.isInstanceOf(RouteException.class)
			.extracting(exception -> ((RouteException)exception).getErrorCode())
			.isEqualTo(RouteErrorCode.ROUTE_SEARCH_EXPIRED);
		verify(routeSessionCommandService, never()).saveActiveSessionIfAbsent(
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any());
	}

	@Test
	@DisplayName("검색 묶음 안에 routeId가 없으면 RT4042를 그대로 반환한다")
	void selectPropagatesMissingCandidateError() {
		when(routeSearchCacheService.getOwnedRouteOrThrow(USER_ID, "rs_walk_test", "missing_route"))
			.thenThrow(new RouteException(RouteErrorCode.ROUTE_CANDIDATE_NOT_FOUND));

		assertThatThrownBy(() -> service.select(USER_ID, "missing_route", new SelectRouteRequest("rs_walk_test")))
			.isInstanceOf(RouteException.class)
			.extracting(exception -> ((RouteException)exception).getErrorCode())
			.isEqualTo(RouteErrorCode.ROUTE_CANDIDATE_NOT_FOUND);
		verify(routeSessionCommandService, never()).saveActiveSessionIfAbsent(
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any());
	}

	@Test
	@DisplayName("다른 사용자의 searchId이면 A4030을 그대로 반환한다")
	void selectPropagatesOwnerMismatchError() {
		when(routeSearchCacheService.getOwnedRouteOrThrow(USER_ID, "rs_other_user", "rt_selected_001"))
			.thenThrow(new RouteException(RouteErrorCode.ROUTE_ACCESS_DENIED));

		assertThatThrownBy(() -> service.select(USER_ID, "rt_selected_001", new SelectRouteRequest("rs_other_user")))
			.isInstanceOf(RouteException.class)
			.extracting(exception -> ((RouteException)exception).getErrorCode())
			.isEqualTo(RouteErrorCode.ROUTE_ACCESS_DENIED);
		verify(routeSessionCommandService, never()).saveActiveSessionIfAbsent(
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any());
	}

	@Test
	@DisplayName("ACTIVE unique 제약 충돌 후 세션이 확인되면 중복 select 성공으로 처리한다")
	void selectTreatsUniqueConflictAsDuplicateWhenActiveSessionExists() {
		RouteSummaryResponse route = transitRoute("rt_selected_001");
		when(routeSearchCacheService.getOwnedRouteOrThrow(USER_ID, "rs_transit_test", "rt_selected_001"))
			.thenReturn(route);
		org.mockito.Mockito.doThrow(activeRouteUniqueViolation())
			.when(routeSessionCommandService)
			.saveActiveSessionIfAbsent(
				org.mockito.ArgumentMatchers.eq(USER_ID),
				org.mockito.ArgumentMatchers.eq("rt_selected_001"),
				org.mockito.ArgumentMatchers.any(Point.class),
				org.mockito.ArgumentMatchers.any(Point.class),
				org.mockito.ArgumentMatchers.any(JsonNode.class));
		when(routeSessionCommandService.hasActiveSession(USER_ID, "rt_selected_001")).thenReturn(true);

		service.select(USER_ID, "rt_selected_001", new SelectRouteRequest("rs_transit_test"));
	}

	@Test
	@DisplayName("ACTIVE unique 제약 충돌 후 세션이 없으면 DB 예외를 전파한다")
	void selectPropagatesActiveRouteUniqueViolationWhenActiveSessionIsMissing() {
		RouteSummaryResponse route = transitRoute("rt_selected_001");
		when(routeSearchCacheService.getOwnedRouteOrThrow(USER_ID, "rs_transit_test", "rt_selected_001"))
			.thenReturn(route);
		org.mockito.Mockito.doThrow(activeRouteUniqueViolation())
			.when(routeSessionCommandService)
			.saveActiveSessionIfAbsent(
				org.mockito.ArgumentMatchers.eq(USER_ID),
				org.mockito.ArgumentMatchers.eq("rt_selected_001"),
				org.mockito.ArgumentMatchers.any(Point.class),
				org.mockito.ArgumentMatchers.any(Point.class),
				org.mockito.ArgumentMatchers.any(JsonNode.class));
		when(routeSessionCommandService.hasActiveSession(USER_ID, "rt_selected_001")).thenReturn(false);

		assertThatThrownBy(() -> service.select(USER_ID, "rt_selected_001", new SelectRouteRequest("rs_transit_test")))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("ACTIVE unique 제약 충돌이 아니면 세션이 확인되어도 DB 예외를 전파한다")
	void selectPropagatesDataIntegrityViolationWhenConstraintIsNotActiveRouteUnique() {
		RouteSummaryResponse route = transitRoute("rt_selected_001");
		when(routeSearchCacheService.getOwnedRouteOrThrow(USER_ID, "rs_transit_test", "rt_selected_001"))
			.thenReturn(route);
		org.mockito.Mockito.doThrow(new DataIntegrityViolationException("unknown constraint"))
			.when(routeSessionCommandService)
			.saveActiveSessionIfAbsent(
				org.mockito.ArgumentMatchers.eq(USER_ID),
				org.mockito.ArgumentMatchers.eq("rt_selected_001"),
				org.mockito.ArgumentMatchers.any(Point.class),
				org.mockito.ArgumentMatchers.any(Point.class),
				org.mockito.ArgumentMatchers.any(JsonNode.class));
		when(routeSessionCommandService.hasActiveSession(USER_ID, "rt_selected_001")).thenReturn(true);

		assertThatThrownBy(() -> service.select(USER_ID, "rt_selected_001", new SelectRouteRequest("rs_transit_test")))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private DataIntegrityViolationException activeRouteUniqueViolation() {
		SQLException sqlException = new SQLException(
			"duplicate key value violates unique constraint \"uk_route_sessions_user_active_route\"",
			"23505");
		ConstraintViolationException constraintViolationException = new ConstraintViolationException(
			"could not execute statement",
			sqlException,
			"uk_route_sessions_user_active_route");
		return new DataIntegrityViolationException("duplicate active route", constraintViolationException);
	}

	private RouteSummaryResponse transitRoute(String routeId) {
		return new RouteSummaryResponse(
			routeId,
			TransportMode.PUBLIC_TRANSIT,
			RouteOption.RECOMMENDED,
			List.of(RouteOption.RECOMMENDED),
			"강서구13 경로",
			BigDecimal.valueOf(2500),
			900,
			15,
			List.of(RouteBadge.CROSSWALK),
			"LINESTRING(128.936 35.12, 128.946 35.13, 128.956 35.14)",
			List.of(new RouteLegResponse(
				1,
				TransportMode.BUS,
				RouteLegRole.TRANSIT,
				"강서구13번 버스에 탑승하세요.",
				BigDecimal.valueOf(2500),
				900,
				15,
				"LINESTRING(128.936 35.12, 128.956 35.14)",
				List.of(),
				"강서구13",
				List.of(new TransitLaneOptionResponse("강서구13", 5, 900, 15, true)),
				new RouteStopResponse("출발 정류장", BigDecimal.valueOf(35.12), BigDecimal.valueOf(128.936)),
				new RouteStopResponse("도착 정류장", BigDecimal.valueOf(35.14), BigDecimal.valueOf(128.956)),
				true,
				List.of())));
	}

	private RouteLegResponse walkLeg(String geometry) {
		return new RouteLegResponse(
			1,
			TransportMode.WALK,
			RouteLegRole.WALK_ONLY,
			"목적지까지 이동하세요.",
			BigDecimal.valueOf(100),
			60,
			1,
			geometry,
			List.of());
	}
}
