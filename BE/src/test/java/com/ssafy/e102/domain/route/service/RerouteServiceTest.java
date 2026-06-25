package com.ssafy.e102.domain.route.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.e102.domain.route.dto.request.RerouteRequest;
import com.ssafy.e102.domain.route.dto.request.WalkRouteSearchRequest;
import com.ssafy.e102.domain.route.dto.response.RerouteResponse;
import com.ssafy.e102.domain.route.dto.response.RouteLegResponse;
import com.ssafy.e102.domain.route.dto.response.RouteSummaryResponse;
import com.ssafy.e102.domain.route.dto.response.WalkRouteSearchResponse;
import com.ssafy.e102.domain.route.entity.RouteSession;
import com.ssafy.e102.domain.route.exception.RouteErrorCode;
import com.ssafy.e102.domain.route.exception.RouteException;
import com.ssafy.e102.domain.route.repository.RouteSessionRepository;
import com.ssafy.e102.domain.route.type.RouteBadge;
import com.ssafy.e102.domain.route.type.RouteLegRole;
import com.ssafy.e102.domain.route.type.RouteOption;
import com.ssafy.e102.domain.route.type.TransportMode;
import com.ssafy.e102.domain.user.entity.User;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

class RerouteServiceTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

	private RouteSessionRepository routeSessionRepository;
	private RouteSessionCommandService routeSessionCommandService;
	private WalkRouteSearchService walkRouteSearchService;
	private TransitRouteSearchService transitRouteSearchService;
	private RerouteService service;

	@BeforeEach
	void setUp() {
		routeSessionRepository = mock(RouteSessionRepository.class);
		routeSessionCommandService = mock(RouteSessionCommandService.class);
		walkRouteSearchService = mock(WalkRouteSearchService.class);
		transitRouteSearchService = mock(TransitRouteSearchService.class);
		service = new RerouteService(routeSessionRepository, routeSessionCommandService, objectMapper,
			walkRouteSearchService,
			transitRouteSearchService);
	}

	@Test
	@DisplayName("routeId 또는 currentPoint가 없으면 RT4001로 차단한다")
	void rejectMissingRequiredFields() {
		UUID userId = UUID.randomUUID();

		assertRouteError(
			() -> service.reroute(userId, new RerouteRequest(null, new GeoPointRequest(35.12, 128.936))),
			RouteErrorCode.INVALID_REROUTE_REQUEST);
		assertRouteError(
			() -> service.reroute(userId, new RerouteRequest("rt_001", null)),
			RouteErrorCode.INVALID_REROUTE_REQUEST);
	}

	@Test
	@DisplayName("현재 위치 좌표 형식 오류는 RT4005로 차단한다")
	void rejectInvalidCurrentPointFormat() {
		assertRouteError(
			() -> service.reroute(UUID.randomUUID(), new RerouteRequest("rt_001", new GeoPointRequest(null, 128.936))),
			RouteErrorCode.INVALID_CURRENT_POINT);
		assertRouteError(
			() -> service.reroute(UUID.randomUUID(), new RerouteRequest("rt_001", new GeoPointRequest(91.0, 128.936))),
			RouteErrorCode.INVALID_CURRENT_POINT);
	}

	@Test
	@DisplayName("현재 위치가 부산 서비스 영역 밖이면 RT4003으로 차단한다")
	void rejectOutOfServiceAreaCurrentPoint() {
		assertRouteError(
			() -> service.reroute(UUID.randomUUID(),
				new RerouteRequest("rt_001", new GeoPointRequest(37.5665, 126.9780))),
			RouteErrorCode.OUT_OF_SERVICE_AREA);
	}

	@Test
	@DisplayName("사용자 소유 route session이 없고 같은 routeId가 다른 사용자에게 있으면 A4030으로 차단한다")
	void rejectRouteSessionOwnedByOtherUser() {
		UUID userId = UUID.randomUUID();
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdOrderByUpdatedAtDesc(userId, "rt_other"))
			.thenReturn(Optional.empty());
		when(routeSessionRepository.findFirstByRouteIdOrderByUpdatedAtDesc("rt_other"))
			.thenReturn(Optional.of(mock(RouteSession.class)));

		assertRouteError(
			() -> service.reroute(userId, new RerouteRequest("rt_other", new GeoPointRequest(35.12, 128.936))),
			RouteErrorCode.ROUTE_ACCESS_DENIED);
	}

	@Test
	@DisplayName("사용자 소유 route session과 복구 가능한 snapshot이 없으면 RT4043으로 차단한다")
	void rejectMissingRouteSession() {
		UUID userId = UUID.randomUUID();
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdOrderByUpdatedAtDesc(userId, "rt_missing"))
			.thenReturn(Optional.empty());
		when(routeSessionRepository.findFirstByRouteIdOrderByUpdatedAtDesc("rt_missing"))
			.thenReturn(Optional.empty());

		assertRouteError(
			() -> service.reroute(userId, new RerouteRequest("rt_missing", new GeoPointRequest(35.12, 128.936))),
			RouteErrorCode.ROUTE_SESSION_NOT_FOUND);
	}

	@Test
	@DisplayName("route session snapshot을 복구할 수 없으면 RT4043으로 차단한다")
	void rejectBrokenRouteSnapshot() {
		UUID userId = UUID.randomUUID();
		RouteSession routeSession = mock(RouteSession.class);
		when(routeSession.getRouteSnapshotJson()).thenReturn(objectMapper.createObjectNode().put("routeId", "rt_001"));
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdOrderByUpdatedAtDesc(userId, "rt_001"))
			.thenReturn(Optional.of(routeSession));

		assertRouteError(
			() -> service.reroute(userId, new RerouteRequest("rt_001", new GeoPointRequest(35.12, 128.936))),
			RouteErrorCode.ROUTE_SESSION_NOT_FOUND);
	}

	@Test
	@DisplayName("route session snapshot에 backendMetadata가 있어도 FE route payload를 복구한다")
	void restoresRouteSnapshotWithBackendMetadata() {
		UUID userId = UUID.randomUUID();
		ObjectNode snapshot = objectMapper.valueToTree(routeSummary("rt_001"));
		snapshot.set("backendMetadata", objectMapper.createObjectNode().put("mapObj", "map-object"));
		RouteSession routeSession = mock(RouteSession.class);
		when(routeSession.getRouteSnapshotJson()).thenReturn(snapshot);
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdOrderByUpdatedAtDesc(userId, "rt_001"))
			.thenReturn(Optional.of(routeSession));

		RerouteResponse response = service.reroute(
			userId,
			new RerouteRequest("rt_001", new GeoPointRequest(35.12001, 128.93601)));

		assertThat(response.route()).isNull();
	}

	@Test
	@DisplayName("현재 위치가 route geometry 10m 이하면 새 route 없이 반환한다")
	void returnsNoRerouteNeededWhenCurrentPointIsStillNearRouteGeometry() {
		UUID userId = UUID.randomUUID();
		RouteSession routeSession = routeSession(routeSummary("rt_001"));
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdOrderByUpdatedAtDesc(userId, "rt_001"))
			.thenReturn(Optional.of(routeSession));

		RerouteResponse response = service.reroute(
			userId,
			new RerouteRequest("rt_001", new GeoPointRequest(35.12001, 128.93601)));

		assertThat(response.route()).isNull();
		verify(routeSessionCommandService, never()).saveActiveSessionIfAbsent(
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any());
		verify(walkRouteSearchService, never()).search(org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any());
		verify(transitRouteSearchService, never()).search(org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any());
	}

	@Test
	@DisplayName("기존 route의 어느 segment든 10m 이내면 새 route 없이 반환한다")
	void returnsNoRerouteNeededWhenCurrentPointIsNearAnyRouteSegment() {
		UUID userId = UUID.randomUUID();
		RouteSession routeSession = routeSession(routeSummary("rt_001"));
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdOrderByUpdatedAtDesc(userId, "rt_001"))
			.thenReturn(Optional.of(routeSession));

		RerouteResponse response = service.reroute(
			userId,
			new RerouteRequest("rt_001", new GeoPointRequest(35.12099, 128.93699)));

		assertThat(response.route()).isNull();
		verify(routeSessionCommandService, never()).saveActiveSessionIfAbsent(
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any());
		verifyNoInteractions(walkRouteSearchService, transitRouteSearchService);
	}

	@Test
	@DisplayName("기존 route geometry 10m 초과 500m 이내 이탈은 전체 재탐색 route를 반환한다")
	void returnsFullRerouteWhenCurrentPointLeavesRouteGeometry() {
		UUID userId = UUID.randomUUID();
		RouteSession routeSession = routeSession(routeSummary("rt_001"));
		RouteSummaryResponse reroutedRoute = routeSummary("rt_full_near_001");
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdOrderByUpdatedAtDesc(userId, "rt_001"))
			.thenReturn(Optional.of(routeSession));
		when(walkRouteSearchService.search(userId, new WalkRouteSearchRequest(
			new GeoPointRequest(35.1195, 128.9360),
			new GeoPointRequest(35.1315, 128.8823))))
			.thenReturn(new WalkRouteSearchResponse("rs_walk_reroute", List.of(reroutedRoute)));

		RerouteResponse response = service.reroute(
			userId,
			new RerouteRequest("rt_001", new GeoPointRequest(35.1195, 128.9360)));

		assertThat(response.route().routeId()).startsWith("rr_full_");
		assertThat(response.route().routeId()).isNotEqualTo("rt_001");
		assertThat(response.route().routeId()).isNotEqualTo(reroutedRoute.routeId());
		verify(walkRouteSearchService).search(userId, new WalkRouteSearchRequest(
			new GeoPointRequest(35.1195, 128.9360),
			new GeoPointRequest(35.1315, 128.8823)));
		assertSavedRerouteSession(response.route().routeId(), 35.1195, 128.9360);
	}

	@Test
	@DisplayName("기존 route geometry에서 크게 이탈해도 500m 이내면 전체 재탐색 route를 반환한다")
	void returnsFullRerouteWhenCurrentPointIsFarFromRouteGeometry() {
		UUID userId = UUID.randomUUID();
		RouteSession routeSession = routeSession(routeSummary("rt_001"));
		RouteSummaryResponse reroutedRoute = routeSummary("rt_full_001");
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdOrderByUpdatedAtDesc(userId, "rt_001"))
			.thenReturn(Optional.of(routeSession));
		when(walkRouteSearchService.search(userId, new WalkRouteSearchRequest(
			new GeoPointRequest(35.1200, 128.9400),
			new GeoPointRequest(35.1315, 128.8823))))
			.thenReturn(new WalkRouteSearchResponse("rs_walk_reroute", List.of(reroutedRoute)));

		RerouteResponse response = service.reroute(
			userId,
			new RerouteRequest("rt_001", new GeoPointRequest(35.1200, 128.9400)));

		assertThat(response.route().routeId()).startsWith("rr_full_");
		assertThat(response.route().routeId()).isNotEqualTo(reroutedRoute.routeId());
		verify(walkRouteSearchService).search(userId, new WalkRouteSearchRequest(
			new GeoPointRequest(35.1200, 128.9400),
			new GeoPointRequest(35.1315, 128.8823)));
		assertSavedRerouteSession(response.route().routeId(), 35.1200, 128.9400);
	}

	@Test
	@DisplayName("10m 초과 이탈도 전체 재탐색 후보가 없으면 RT4040을 반환한다")
	void returnsRouteNotFoundWhenNearFullReroutePathIsMissing() {
		UUID userId = UUID.randomUUID();
		RouteSession routeSession = routeSession(routeSummary("rt_001"));
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdOrderByUpdatedAtDesc(userId, "rt_001"))
			.thenReturn(Optional.of(routeSession));
		when(walkRouteSearchService.search(userId, new WalkRouteSearchRequest(
			new GeoPointRequest(35.1195, 128.9360),
			new GeoPointRequest(35.1315, 128.8823))))
			.thenThrow(new RouteException(RouteErrorCode.ROUTE_NOT_FOUND));

		assertRouteError(
			() -> service.reroute(userId, new RerouteRequest("rt_001", new GeoPointRequest(35.1195, 128.9360))),
			RouteErrorCode.ROUTE_NOT_FOUND);
	}

	@Test
	@DisplayName("전체 재탐색 후보가 없으면 기존 no-route 오류 RT4040을 반환한다")
	void returnsRouteNotFoundWhenFullReroutePathIsMissing() {
		UUID userId = UUID.randomUUID();
		RouteSession routeSession = routeSession(routeSummary("rt_001"));
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdOrderByUpdatedAtDesc(userId, "rt_001"))
			.thenReturn(Optional.of(routeSession));
		when(walkRouteSearchService.search(userId, new WalkRouteSearchRequest(
			new GeoPointRequest(35.1200, 128.9400),
			new GeoPointRequest(35.1315, 128.8823))))
			.thenReturn(new WalkRouteSearchResponse("rs_walk_reroute", List.of()));

		assertRouteError(
			() -> service.reroute(userId, new RerouteRequest("rt_001", new GeoPointRequest(35.1200, 128.9400))),
			RouteErrorCode.ROUTE_NOT_FOUND);
	}

	@Test
	@DisplayName("기존 route geometry 500m 초과 이탈은 RT4091로 새 검색 fallback을 유도한다")
	void rejectTooFarCurrentPoint() {
		UUID userId = UUID.randomUUID();
		RouteSession routeSession = routeSession(routeSummary("rt_001"));
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdOrderByUpdatedAtDesc(userId, "rt_001"))
			.thenReturn(Optional.of(routeSession));

		assertRouteError(
			() -> service.reroute(userId, new RerouteRequest("rt_001", new GeoPointRequest(35.1200, 128.9500))),
			RouteErrorCode.ROUTE_TOO_FAR_FOR_REROUTE);
		verifyNoInteractions(walkRouteSearchService, transitRouteSearchService);
	}

	@Test
	@DisplayName("route geometry WKT를 파싱할 수 없으면 RT4043으로 차단한다")
	void rejectBrokenRouteGeometry() {
		UUID userId = UUID.randomUUID();
		RouteSession routeSession = routeSession(new RouteSummaryResponse(
			"rt_001",
			TransportMode.WALK,
			RouteOption.SAFE,
			"안전 경로",
			BigDecimal.valueOf(120),
			90,
			2,
			List.of(RouteBadge.LOW_SLOPE),
			"BROKEN",
			List.of()));
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdOrderByUpdatedAtDesc(userId, "rt_001"))
			.thenReturn(Optional.of(routeSession));

		assertRouteError(
			() -> service.reroute(userId, new RerouteRequest("rt_001", new GeoPointRequest(35.12, 128.936))),
			RouteErrorCode.ROUTE_SESSION_NOT_FOUND);
	}

	private RouteSummaryResponse routeSummary(String routeId) {
		return new RouteSummaryResponse(
			routeId,
			TransportMode.WALK,
			RouteOption.SAFE,
			"안전 경로",
			BigDecimal.valueOf(120),
			90,
			2,
			List.of(RouteBadge.LOW_SLOPE),
			"LINESTRING(128.936 35.12, 128.937 35.121)",
			List.of(new RouteLegResponse(
				1,
				TransportMode.WALK,
				RouteLegRole.WALK_ONLY,
				"목적지까지 이동하세요.",
				BigDecimal.valueOf(120),
				90,
				2,
				"LINESTRING(128.936 35.12, 128.937 35.121)",
				List.of())));
	}

	private RouteSession routeSession(RouteSummaryResponse route) {
		RouteSession routeSession = mock(RouteSession.class);
		when(routeSession.getUser()).thenReturn(mock(User.class));
		when(routeSession.getRouteSnapshotJson()).thenReturn(objectMapper.valueToTree(route));
		when(routeSession.getEndPoint()).thenReturn(GEOMETRY_FACTORY.createPoint(new Coordinate(128.8823, 35.1315)));
		return routeSession;
	}

	private void assertSavedRerouteSession(String routeId, double expectedLat, double expectedLng) {
		ArgumentCaptor<org.locationtech.jts.geom.Point> startPointCaptor = ArgumentCaptor
			.forClass(org.locationtech.jts.geom.Point.class);
		ArgumentCaptor<com.fasterxml.jackson.databind.JsonNode> snapshotCaptor = ArgumentCaptor
			.forClass(com.fasterxml.jackson.databind.JsonNode.class);
		verify(routeSessionCommandService).saveActiveSessionIfAbsent(
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.eq(routeId),
			startPointCaptor.capture(),
			org.mockito.ArgumentMatchers.any(),
			snapshotCaptor.capture());
		assertThat(startPointCaptor.getValue().getY()).isEqualTo(expectedLat);
		assertThat(startPointCaptor.getValue().getX()).isEqualTo(expectedLng);
		assertThat(snapshotCaptor.getValue().get("routeId").asText()).isEqualTo(routeId);
	}

	private void assertRouteError(Runnable action, RouteErrorCode expectedErrorCode) {
		assertThatThrownBy(action::run)
			.isInstanceOf(RouteException.class)
			.extracting(exception -> ((RouteException)exception).getErrorCode())
			.isEqualTo(expectedErrorCode);
	}
}
