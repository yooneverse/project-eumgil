package com.ssafy.e102.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.ssafy.e102.domain.report.dto.request.HazardReportRerouteRequest;
import com.ssafy.e102.domain.report.dto.response.HazardReportRerouteResponse;
import com.ssafy.e102.domain.report.entity.HazardReport;
import com.ssafy.e102.domain.report.exception.HazardReportErrorCode;
import com.ssafy.e102.domain.report.exception.HazardReportException;
import com.ssafy.e102.domain.report.repository.HazardReportRepository;
import com.ssafy.e102.domain.report.type.ReportStatus;
import com.ssafy.e102.domain.report.type.ReportType;
import com.ssafy.e102.domain.route.dto.response.RouteGuidanceEventResponse;
import com.ssafy.e102.domain.route.dto.response.RouteLegResponse;
import com.ssafy.e102.domain.route.dto.response.RouteGuidanceEventType;
import com.ssafy.e102.domain.route.dto.response.RouteStopResponse;
import com.ssafy.e102.domain.route.dto.response.RouteSummaryResponse;
import com.ssafy.e102.domain.route.entity.RouteSession;
import com.ssafy.e102.domain.route.exception.RouteErrorCode;
import com.ssafy.e102.domain.route.exception.RouteException;
import com.ssafy.e102.domain.route.repository.RouteSessionRepository;
import com.ssafy.e102.domain.route.service.RouteProjectionGeometryService;
import com.ssafy.e102.domain.route.service.RouteSessionCommandService;
import com.ssafy.e102.domain.route.service.WalkRouteProfileService;
import com.ssafy.e102.domain.route.service.WalkRouteUserProfile;
import com.ssafy.e102.domain.route.service.WalkRouteUserProfileQueryService;
import com.ssafy.e102.domain.route.service.WalkRoutePayloadService;
import com.ssafy.e102.domain.route.type.RouteLegRole;
import com.ssafy.e102.domain.route.type.RouteOption;
import com.ssafy.e102.domain.route.type.RouteSessionStatus;
import com.ssafy.e102.domain.route.type.TransportMode;
import com.ssafy.e102.domain.route.type.WalkRouteProfile;
import com.ssafy.e102.domain.user.entity.User;
import com.ssafy.e102.domain.user.type.MobilitySubtype;
import com.ssafy.e102.domain.user.type.PrimaryUserType;
import com.ssafy.e102.domain.user.type.SocialProvider;
import com.ssafy.e102.global.external.graphhopper.GraphHopperRouteClient;
import com.ssafy.e102.global.external.graphhopper.GraphHopperRoutePath;
import com.ssafy.e102.global.external.graphhopper.GraphHopperRouteRequest;
import com.ssafy.e102.global.geo.GeoPointConverter;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

class HazardReportRerouteServiceTest {

	private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

	@Mock
	private HazardReportRepository hazardReportRepository;

	@Mock
	private RouteSessionRepository routeSessionRepository;

	@Mock
	private RouteSessionCommandService routeSessionCommandService;

	@Mock
	private RouteProjectionGeometryService routeProjectionGeometryService;

	@Mock
	private HazardReportAvoidAreaBuilder hazardReportAvoidAreaBuilder;

	@Mock
	private HazardReportRerouteCustomModelFactory hazardReportRerouteCustomModelFactory;

	@Mock
	private GraphHopperRouteClient graphHopperRouteClient;

	@Mock
	private WalkRoutePayloadService walkRoutePayloadService;

	@Mock
	private WalkRouteUserProfileQueryService walkRouteUserProfileQueryService;

	@Mock
	private WalkRouteProfileService walkRouteProfileService;

	private HazardReportRerouteService service;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final GeoPointConverter geoPointConverter = new GeoPointConverter();

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		service = new HazardReportRerouteService(
			hazardReportRepository,
			routeSessionRepository,
			routeSessionCommandService,
			routeProjectionGeometryService,
			hazardReportAvoidAreaBuilder,
			hazardReportRerouteCustomModelFactory,
			graphHopperRouteClient,
			walkRoutePayloadService,
			walkRouteUserProfileQueryService,
			walkRouteProfileService,
			objectMapper);
	}

	@Test
	@DisplayName("reroute is rejected when the report is not owned by the authenticated user")
	void rejectOtherUsersReport() {
		UUID userId = UUID.randomUUID();
		when(hazardReportRepository.findWithImagesByReportId(12L))
			.thenReturn(Optional.of(report(UUID.randomUUID(), 12L, 35.1, 129.1)));

		assertThatThrownBy(() -> service.reroute(
			userId,
			12L,
			new HazardReportRerouteRequest("rr_active_123", new GeoPointRequest(35.1, 129.1), null)))
			.isInstanceOf(HazardReportException.class)
			.extracting("errorCode")
			.isEqualTo(HazardReportErrorCode.HAZARD_REPORT_FORBIDDEN);
	}

	@Test
	@DisplayName("reroute is rejected when the active route session routeId does not match the request")
	void rejectRouteIdMismatch() {
		UUID userId = UUID.randomUUID();
		when(hazardReportRepository.findWithImagesByReportId(12L))
			.thenReturn(Optional.of(report(userId, 12L, 35.1, 129.1)));
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(
			eq(userId),
			eq("rr_active_123"),
			eq(RouteSessionStatus.ACTIVE)))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.reroute(
			userId,
			12L,
			new HazardReportRerouteRequest("rr_active_123", new GeoPointRequest(35.1, 129.1), null)))
			.isInstanceOf(RouteException.class)
			.extracting("errorCode")
			.isEqualTo(RouteErrorCode.ROUTE_SESSION_NOT_FOUND);

		verify(routeSessionRepository).findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(
			eq(userId),
			eq("rr_active_123"),
			eq(RouteSessionStatus.ACTIVE));
		verify(routeSessionRepository, never()).findFirstByUser_UserIdAndStatusOrderByUpdatedAtDesc(eq(userId), any());
		verify(graphHopperRouteClient, never()).routeWithCustomModel(any(), any());
	}

	@Test
	@DisplayName("reroute preserves the user's walk profile when requesting the alternate route")
	void rerouteUsesUserWalkProfile() {
		UUID userId = UUID.randomUUID();
		HazardReport report = report(userId, 12L, 35.1200, 129.0000);
		RouteSession routeSession = routeSession("rr_active_123");
		GraphHopperRoutePath reroutedPath = new GraphHopperRoutePath(
			BigDecimal.valueOf(80),
			60_000L,
			List.of(),
			java.util.Map.of());
		RouteSummaryResponse reroutedRoute = routeSummary("rerouted-route-id", RouteOption.SHORTEST);
		when(hazardReportRepository.findWithImagesByReportId(12L)).thenReturn(Optional.of(report));
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(
			eq(userId),
			eq("rr_active_123"),
			eq(RouteSessionStatus.ACTIVE)))
			.thenReturn(Optional.of(routeSession));
		when(routeProjectionGeometryService.restoreRouteSnapshot(routeSession))
			.thenReturn(routeSummary("rr_active_123", RouteOption.SHORTEST));
		when(routeProjectionGeometryService.projectRoutePoint(any(RouteSummaryResponse.class), any(Point.class)))
			.thenReturn(new RouteProjectionGeometryService.ProjectedRoutePoint(
				new Coordinate(129.0000, 35.1200),
				0,
				5.0,
				new Coordinate(128.9999, 35.1200),
				new Coordinate(129.0001, 35.1200)));
		when(hazardReportAvoidAreaBuilder.build(any(), any(), any()))
			.thenReturn(GEOMETRY_FACTORY.createPolygon(new Coordinate[] {
				new Coordinate(128.9999, 35.1199),
				new Coordinate(129.0001, 35.1199),
				new Coordinate(129.0001, 35.1201),
				new Coordinate(128.9999, 35.1201),
				new Coordinate(128.9999, 35.1199)
			}));
		when(hazardReportRerouteCustomModelFactory.create(any()))
			.thenReturn(JsonNodeFactory.instance.objectNode());
		when(walkRouteUserProfileQueryService.getProfile(userId))
			.thenReturn(new WalkRouteUserProfile(PrimaryUserType.MOBILITY_IMPAIRED, MobilitySubtype.MANUAL_WHEELCHAIR));
		when(walkRouteProfileService.resolve(
			PrimaryUserType.MOBILITY_IMPAIRED,
			MobilitySubtype.MANUAL_WHEELCHAIR,
			RouteOption.SHORTEST))
			.thenReturn(WalkRouteProfile.WHEELCHAIR_MANUAL_FAST);
		when(graphHopperRouteClient.routeWithCustomModel(any(GraphHopperRouteRequest.class), any()))
			.thenReturn(reroutedPath);
		when(walkRoutePayloadService.toRouteSummary(any(), any()))
			.thenReturn(reroutedRoute);

		HazardReportRerouteResponse response = service.reroute(
			userId,
			12L,
			new HazardReportRerouteRequest("rr_active_123", new GeoPointRequest(35.1, 129.1), null));

		assertThat(response.rerouted()).isTrue();
		verify(graphHopperRouteClient).routeWithCustomModel(
			argThat(request -> request.profile() == WalkRouteProfile.WHEELCHAIR_MANUAL_FAST),
			any());
		verify(walkRoutePayloadService).toRouteSummary(
			eq("rr_active_123"),
			argThat(candidate -> candidate.profile() == WalkRouteProfile.WHEELCHAIR_MANUAL_FAST));
	}

	@Test
	@DisplayName("reroute rebuilds a full public transit route when the active leg is walk to transit")
	void rerouteTransitWalkToTransitLeg() {
		UUID userId = UUID.randomUUID();
		HazardReport report = report(userId, 12L, 35.1200, 129.0000);
		RouteSummaryResponse transitRoute = transitRouteSummary("pt_route_123", RouteLegRole.WALK_TO_TRANSIT);
		RouteSession routeSession = routeSession(transitRoute);
		GraphHopperRoutePath reroutedPath = new GraphHopperRoutePath(
			BigDecimal.valueOf(90),
			75_000L,
			List.of(),
			java.util.Map.of());
		RouteLegResponse reroutedWalkLeg = new RouteLegResponse(
			1,
			TransportMode.WALK,
			RouteLegRole.WALK_TO_TRANSIT,
			"Walk to boarding stop",
			BigDecimal.valueOf(90),
			75,
			1,
			"LINESTRING(129.0002 35.1202, 129.0004 35.1204)",
			List.of());
		when(hazardReportRepository.findWithImagesByReportId(12L)).thenReturn(Optional.of(report));
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(
			eq(userId),
			eq("pt_route_123"),
			eq(RouteSessionStatus.ACTIVE)))
			.thenReturn(Optional.of(routeSession));
		when(routeProjectionGeometryService.restoreRouteSnapshot(routeSession)).thenReturn(transitRoute);
		when(routeProjectionGeometryService.projectRoutePoint(any(RouteSummaryResponse.class), any(GeoPointRequest.class)))
			.thenAnswer(invocation -> projectedLegPoint(
				((RouteSummaryResponse)invocation.getArgument(0)).legs().get(0).sequence(),
				new Coordinate(129.0002, 35.1202),
				new Coordinate(129.0000, 35.1200),
				new Coordinate(129.0004, 35.1204)));
		when(routeProjectionGeometryService.projectRoutePoint(any(RouteSummaryResponse.class), any(Point.class)))
			.thenReturn(projectedLegPoint(
				1,
				new Coordinate(129.0002, 35.1202),
				new Coordinate(129.0000, 35.1200),
				new Coordinate(129.0004, 35.1204)));
		when(hazardReportAvoidAreaBuilder.build(any(), any(), any()))
			.thenReturn(GEOMETRY_FACTORY.createPolygon(new Coordinate[] {
				new Coordinate(129.0001, 35.1201),
				new Coordinate(129.0003, 35.1201),
				new Coordinate(129.0003, 35.1203),
				new Coordinate(129.0001, 35.1203),
				new Coordinate(129.0001, 35.1201)
			}));
		when(hazardReportRerouteCustomModelFactory.create(any()))
			.thenReturn(JsonNodeFactory.instance.objectNode());
		when(walkRouteUserProfileQueryService.getProfile(userId))
			.thenReturn(new WalkRouteUserProfile(PrimaryUserType.LOW_VISION, null));
		when(walkRouteProfileService.resolve(PrimaryUserType.LOW_VISION, null, RouteOption.SAFE))
			.thenReturn(WalkRouteProfile.VISUAL_SAFE);
		when(graphHopperRouteClient.routeWithCustomModel(any(GraphHopperRouteRequest.class), any()))
			.thenReturn(reroutedPath);
		when(walkRoutePayloadService.toWalkLeg(
			eq(1),
			eq(RouteLegRole.WALK_TO_TRANSIT),
			any(String.class),
			eq(reroutedPath),
			eq(WalkRouteProfile.VISUAL_SAFE),
			isNull(),
			eq(RouteGuidanceEventType.BUS_STOP)))
			.thenReturn(reroutedWalkLeg);

		HazardReportRerouteResponse response = service.reroute(
			userId,
			12L,
			new HazardReportRerouteRequest("pt_route_123", new GeoPointRequest(35.1202, 129.0002), 1));

		assertThat(response.rerouted()).isTrue();
		assertThat(response.route()).isNotNull();
		assertThat(response.route().transportMode()).isEqualTo(TransportMode.PUBLIC_TRANSIT);
		assertThat(response.route().legs()).hasSize(3);
		assertThat(response.route().legs().get(0).geometry()).isEqualTo(reroutedWalkLeg.geometry());
		assertThat(response.route().legs().get(1).geometry()).isEqualTo(transitRoute.legs().get(1).geometry());
	}

	@Test
	@DisplayName("reroute rebuilds a full public transit route when the active leg is walk to destination")
	void rerouteTransitWalkToDestinationLeg() {
		UUID userId = UUID.randomUUID();
		HazardReport report = report(userId, 12L, 35.1200, 129.0000);
		RouteSummaryResponse transitRoute = transitRouteSummary("pt_route_456", RouteLegRole.WALK_TO_DESTINATION);
		RouteSession routeSession = routeSession(transitRoute);
		GraphHopperRoutePath reroutedPath = new GraphHopperRoutePath(
			BigDecimal.valueOf(110),
			95_000L,
			List.of(),
			java.util.Map.of());
		RouteLegResponse reroutedWalkLeg = new RouteLegResponse(
			3,
			TransportMode.WALK,
			RouteLegRole.WALK_TO_DESTINATION,
			"Walk to destination",
			BigDecimal.valueOf(110),
			95,
			1,
			"LINESTRING(129.0012 35.1212, 129.0014 35.1214)",
			List.of());
		when(hazardReportRepository.findWithImagesByReportId(12L)).thenReturn(Optional.of(report));
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(
			eq(userId),
			eq("pt_route_456"),
			eq(RouteSessionStatus.ACTIVE)))
			.thenReturn(Optional.of(routeSession));
		when(routeProjectionGeometryService.restoreRouteSnapshot(routeSession)).thenReturn(transitRoute);
		when(routeProjectionGeometryService.projectRoutePoint(any(RouteSummaryResponse.class), any(GeoPointRequest.class)))
			.thenAnswer(invocation -> {
				RouteSummaryResponse route = invocation.getArgument(0);
				int sequence = route.legs().get(0).sequence();
				if (sequence == 3) {
					return projectedLegPoint(
						3,
						new Coordinate(129.0012, 35.1212),
						new Coordinate(129.0010, 35.1210),
						new Coordinate(129.0014, 35.1214));
				}
				return projectedLegPoint(
					sequence,
					new Coordinate(129.0050, 35.1250),
					new Coordinate(129.0000, 35.1200),
					new Coordinate(129.0004, 35.1204));
			});
		when(routeProjectionGeometryService.projectRoutePoint(any(RouteSummaryResponse.class), any(Point.class)))
			.thenReturn(projectedLegPoint(
				3,
				new Coordinate(129.0012, 35.1212),
				new Coordinate(129.0010, 35.1210),
				new Coordinate(129.0014, 35.1214)));
		when(hazardReportAvoidAreaBuilder.build(any(), any(), any()))
			.thenReturn(GEOMETRY_FACTORY.createPolygon(new Coordinate[] {
				new Coordinate(129.0011, 35.1211),
				new Coordinate(129.0013, 35.1211),
				new Coordinate(129.0013, 35.1213),
				new Coordinate(129.0011, 35.1213),
				new Coordinate(129.0011, 35.1211)
			}));
		when(hazardReportRerouteCustomModelFactory.create(any()))
			.thenReturn(JsonNodeFactory.instance.objectNode());
		when(walkRouteUserProfileQueryService.getProfile(userId))
			.thenReturn(new WalkRouteUserProfile(PrimaryUserType.LOW_VISION, null));
		when(walkRouteProfileService.resolve(PrimaryUserType.LOW_VISION, null, RouteOption.SAFE))
			.thenReturn(WalkRouteProfile.VISUAL_SAFE);
		when(graphHopperRouteClient.routeWithCustomModel(any(GraphHopperRouteRequest.class), any()))
			.thenReturn(reroutedPath);
		when(walkRoutePayloadService.toWalkLeg(
			eq(3),
			eq(RouteLegRole.WALK_TO_DESTINATION),
			any(String.class),
			eq(reroutedPath),
			eq(WalkRouteProfile.VISUAL_SAFE),
			isNull(),
			eq(RouteGuidanceEventType.DESTINATION)))
			.thenReturn(reroutedWalkLeg);

		HazardReportRerouteResponse response = service.reroute(
			userId,
			12L,
			new HazardReportRerouteRequest("pt_route_456", new GeoPointRequest(35.1208, 129.0008), 3));

		assertThat(response.rerouted()).isTrue();
		assertThat(response.route()).isNotNull();
		assertThat(response.route().transportMode()).isEqualTo(TransportMode.PUBLIC_TRANSIT);
		assertThat(response.route().legs()).hasSize(3);
		assertThat(response.route().legs().get(2).geometry()).isEqualTo(reroutedWalkLeg.geometry());
		assertThat(response.route().legs().get(1).geometry()).isEqualTo(transitRoute.legs().get(1).geometry());
	}

	@Test
	@DisplayName("reroute returns rerouted false when the active public transit leg is transit")
	void rerouteTransitLegReturnsFalse() {
		UUID userId = UUID.randomUUID();
		HazardReport report = report(userId, 12L, 35.1200, 129.0000);
		RouteSummaryResponse transitRoute = transitRouteSummary("pt_route_789", RouteLegRole.TRANSIT);
		RouteSession routeSession = routeSession(transitRoute);
		when(hazardReportRepository.findWithImagesByReportId(12L)).thenReturn(Optional.of(report));
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(
			eq(userId),
			eq("pt_route_789"),
			eq(RouteSessionStatus.ACTIVE)))
			.thenReturn(Optional.of(routeSession));
		when(routeProjectionGeometryService.restoreRouteSnapshot(routeSession)).thenReturn(transitRoute);
		when(routeProjectionGeometryService.projectRoutePoint(any(RouteSummaryResponse.class), any(GeoPointRequest.class)))
			.thenAnswer(invocation -> {
				RouteSummaryResponse route = invocation.getArgument(0);
				int sequence = route.legs().get(0).sequence();
				if (sequence == 2) {
					return projectedLegPoint(
						2,
						new Coordinate(129.0007, 35.1207),
						new Coordinate(129.0004, 35.1204),
						new Coordinate(129.0010, 35.1210));
				}
				return projectedLegPoint(
					sequence,
					new Coordinate(129.0050, 35.1250),
					new Coordinate(129.0000, 35.1200),
					new Coordinate(129.0004, 35.1204));
			});

		HazardReportRerouteResponse response = service.reroute(
			userId,
			12L,
			new HazardReportRerouteRequest("pt_route_789", new GeoPointRequest(35.1207, 129.0007), 2));

		assertThat(response.rerouted()).isFalse();
		assertThat(response.route()).isNull();
		verify(graphHopperRouteClient, never()).routeWithCustomModel(any(), any());
	}

	@Test
	@DisplayName("reroute uses the activeLegSequence from the request when public transit legs overlap")
	void rerouteTransitUsesRequestedActiveLegSequence() {
		UUID userId = UUID.randomUUID();
		HazardReport report = report(userId, 12L, 35.1200, 129.0000);
		RouteSummaryResponse transitRoute = transitRouteSummary("pt_route_overlap", RouteLegRole.WALK_TO_TRANSIT);
		RouteSession routeSession = routeSession(transitRoute);
		GraphHopperRoutePath reroutedPath = new GraphHopperRoutePath(
			BigDecimal.valueOf(95),
			80_000L,
			List.of(),
			java.util.Map.of());
		RouteLegResponse reroutedWalkLeg = new RouteLegResponse(
			1,
			TransportMode.WALK,
			RouteLegRole.WALK_TO_TRANSIT,
			"Walk to boarding stop",
			BigDecimal.valueOf(95),
			80,
			1,
			"LINESTRING(129.0002 35.1202, 129.0004 35.1204)",
			List.of());
		when(hazardReportRepository.findWithImagesByReportId(12L)).thenReturn(Optional.of(report));
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(
			eq(userId),
			eq("pt_route_overlap"),
			eq(RouteSessionStatus.ACTIVE)))
			.thenReturn(Optional.of(routeSession));
		when(routeProjectionGeometryService.restoreRouteSnapshot(routeSession)).thenReturn(transitRoute);
		when(routeProjectionGeometryService.projectRoutePoint(any(RouteSummaryResponse.class), any(Point.class)))
			.thenReturn(projectedLegPoint(
				1,
				new Coordinate(129.0002, 35.1202),
				new Coordinate(129.0000, 35.1200),
				new Coordinate(129.0004, 35.1204)));
		when(hazardReportAvoidAreaBuilder.build(any(), any(), any()))
			.thenReturn(GEOMETRY_FACTORY.createPolygon(new Coordinate[] {
				new Coordinate(129.0001, 35.1201),
				new Coordinate(129.0003, 35.1201),
				new Coordinate(129.0003, 35.1203),
				new Coordinate(129.0001, 35.1203),
				new Coordinate(129.0001, 35.1201)
			}));
		when(hazardReportRerouteCustomModelFactory.create(any()))
			.thenReturn(JsonNodeFactory.instance.objectNode());
		when(walkRouteUserProfileQueryService.getProfile(userId))
			.thenReturn(new WalkRouteUserProfile(PrimaryUserType.LOW_VISION, null));
		when(walkRouteProfileService.resolve(PrimaryUserType.LOW_VISION, null, RouteOption.SAFE))
			.thenReturn(WalkRouteProfile.VISUAL_SAFE);
		when(graphHopperRouteClient.routeWithCustomModel(any(GraphHopperRouteRequest.class), any()))
			.thenReturn(reroutedPath);
		when(walkRoutePayloadService.toWalkLeg(
			eq(1),
			eq(RouteLegRole.WALK_TO_TRANSIT),
			any(String.class),
			eq(reroutedPath),
			eq(WalkRouteProfile.VISUAL_SAFE),
			isNull(),
			eq(RouteGuidanceEventType.BUS_STOP)))
			.thenReturn(reroutedWalkLeg);

		HazardReportRerouteResponse response = service.reroute(
			userId,
			12L,
			new HazardReportRerouteRequest("pt_route_overlap", new GeoPointRequest(35.1204, 129.0004), 1));

		assertThat(response.rerouted()).isTrue();
		assertThat(response.route()).isNotNull();
		verify(routeProjectionGeometryService, never()).projectRoutePoint(any(RouteSummaryResponse.class), any(GeoPointRequest.class));
	}

	@Test
	@DisplayName("reroute does not fall back to geometry projection when the requested activeLegSequence is missing")
	void rerouteTransitDoesNotFallbackWhenRequestedActiveLegSequenceIsMissing() {
		UUID userId = UUID.randomUUID();
		HazardReport report = report(userId, 12L, 35.1200, 129.0000);
		RouteSummaryResponse transitRoute = transitRouteSummary("pt_route_missing_leg", RouteLegRole.WALK_TO_TRANSIT);
		RouteSession routeSession = routeSession(transitRoute);
		when(hazardReportRepository.findWithImagesByReportId(12L)).thenReturn(Optional.of(report));
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(
			eq(userId),
			eq("pt_route_missing_leg"),
			eq(RouteSessionStatus.ACTIVE)))
			.thenReturn(Optional.of(routeSession));
		when(routeProjectionGeometryService.restoreRouteSnapshot(routeSession)).thenReturn(transitRoute);

		HazardReportRerouteResponse response = service.reroute(
			userId,
			12L,
			new HazardReportRerouteRequest("pt_route_missing_leg", new GeoPointRequest(35.1204, 129.0004), 99));

		assertThat(response.rerouted()).isFalse();
		assertThat(response.route()).isNull();
		verify(routeProjectionGeometryService, never()).projectRoutePoint(any(RouteSummaryResponse.class), any(GeoPointRequest.class));
		verify(graphHopperRouteClient, never()).routeWithCustomModel(any(), any());
	}

	@Test
	@DisplayName("reroute recalculates route guidance offsets and merged geometry for rebuilt public transit routes")
	void rerouteTransitRecalculatesGuidanceOffsetsAndGeometry() {
		UUID userId = UUID.randomUUID();
		HazardReport report = report(userId, 12L, 35.1200, 129.0000);
		RouteSummaryResponse transitRoute = transitRouteSummaryWithGuidance("pt_route_guidance");
		RouteSession routeSession = routeSession(transitRoute);
		GraphHopperRoutePath reroutedPath = new GraphHopperRoutePath(
			BigDecimal.valueOf(110),
			95_000L,
			List.of(),
			java.util.Map.of());
		RouteLegResponse reroutedWalkLeg = new RouteLegResponse(
			3,
			TransportMode.WALK,
			RouteLegRole.WALK_TO_DESTINATION,
			"Walk to destination",
			BigDecimal.valueOf(110),
			95,
			1,
			"LINESTRING(129.0010 35.1210, 129.0012 35.1212, 129.0014 35.1214)",
			List.of(new RouteGuidanceEventResponse(
				1,
				RouteGuidanceEventType.DESTINATION,
				BigDecimal.valueOf(40),
				30,
				"POINT(129.0012 35.1212)")));
		when(hazardReportRepository.findWithImagesByReportId(12L)).thenReturn(Optional.of(report));
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(
			eq(userId),
			eq("pt_route_guidance"),
			eq(RouteSessionStatus.ACTIVE)))
			.thenReturn(Optional.of(routeSession));
		when(routeProjectionGeometryService.restoreRouteSnapshot(routeSession)).thenReturn(transitRoute);
		when(routeProjectionGeometryService.projectRoutePoint(any(RouteSummaryResponse.class), any(Point.class)))
			.thenReturn(projectedLegPoint(
				3,
				new Coordinate(129.0012, 35.1212),
				new Coordinate(129.0010, 35.1210),
				new Coordinate(129.0014, 35.1214)));
		when(hazardReportAvoidAreaBuilder.build(any(), any(), any()))
			.thenReturn(GEOMETRY_FACTORY.createPolygon(new Coordinate[] {
				new Coordinate(129.0011, 35.1211),
				new Coordinate(129.0013, 35.1211),
				new Coordinate(129.0013, 35.1213),
				new Coordinate(129.0011, 35.1213),
				new Coordinate(129.0011, 35.1211)
			}));
		when(hazardReportRerouteCustomModelFactory.create(any()))
			.thenReturn(JsonNodeFactory.instance.objectNode());
		when(walkRouteUserProfileQueryService.getProfile(userId))
			.thenReturn(new WalkRouteUserProfile(PrimaryUserType.LOW_VISION, null));
		when(walkRouteProfileService.resolve(PrimaryUserType.LOW_VISION, null, RouteOption.SAFE))
			.thenReturn(WalkRouteProfile.VISUAL_SAFE);
		when(graphHopperRouteClient.routeWithCustomModel(any(GraphHopperRouteRequest.class), any()))
			.thenReturn(reroutedPath);
		when(walkRoutePayloadService.toWalkLeg(
			eq(3),
			eq(RouteLegRole.WALK_TO_DESTINATION),
			any(String.class),
			eq(reroutedPath),
			eq(WalkRouteProfile.VISUAL_SAFE),
			isNull(),
			eq(RouteGuidanceEventType.DESTINATION)))
			.thenReturn(reroutedWalkLeg);

		HazardReportRerouteResponse response = service.reroute(
			userId,
			12L,
			new HazardReportRerouteRequest("pt_route_guidance", new GeoPointRequest(35.1212, 129.0012), 3));

		assertThat(response.rerouted()).isTrue();
		assertThat(response.route()).isNotNull();
		assertThat(response.route().geometry()).isEqualTo(
			"LINESTRING(129.0000 35.1200, 129.0004 35.1204, 129.0010 35.1210, 129.0012 35.1212, 129.0014 35.1214)");
		assertThat(response.route().legs().get(1).guidanceEvents().get(0).distanceFromRouteStartMeter())
			.isEqualByComparingTo("180.00");
		assertThat(response.route().legs().get(1).guidanceEvents().get(0).durationFromRouteStartSecond())
			.isEqualTo(105);
		assertThat(response.route().legs().get(2).guidanceEvents().get(0).distanceFromRouteStartMeter())
			.isEqualByComparingTo("860.00");
		assertThat(response.route().legs().get(2).guidanceEvents().get(0).durationFromRouteStartSecond())
			.isEqualTo(540);
	}

	@Test
	@DisplayName("reroute saves an active session snapshot for the returned rerouted routeId")
	void rerouteSavesActiveSessionForReturnedRouteId() {
		UUID userId = UUID.randomUUID();
		HazardReport report = report(userId, 12L, 35.1200, 129.0000);
		RouteSession routeSession = routeSession("rr_active_123");
		GraphHopperRoutePath reroutedPath = new GraphHopperRoutePath(
			BigDecimal.valueOf(80),
			60_000L,
			List.of(),
			java.util.Map.of());
		RouteSummaryResponse reroutedRoute = routeSummary("graphhopper-route-id", RouteOption.SAFE);
		when(hazardReportRepository.findWithImagesByReportId(12L)).thenReturn(Optional.of(report));
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(
			eq(userId),
			eq("rr_active_123"),
			eq(RouteSessionStatus.ACTIVE)))
			.thenReturn(Optional.of(routeSession));
		when(routeProjectionGeometryService.restoreRouteSnapshot(routeSession))
			.thenReturn(routeSummary("rr_active_123", RouteOption.SAFE));
		when(routeProjectionGeometryService.projectRoutePoint(any(RouteSummaryResponse.class), any(Point.class)))
			.thenReturn(new RouteProjectionGeometryService.ProjectedRoutePoint(
				new Coordinate(129.0000, 35.1200),
				0,
				5.0,
				new Coordinate(128.9999, 35.1200),
				new Coordinate(129.0001, 35.1200)));
		when(hazardReportAvoidAreaBuilder.build(any(), any(), any()))
			.thenReturn(GEOMETRY_FACTORY.createPolygon(new Coordinate[] {
				new Coordinate(128.9999, 35.1199),
				new Coordinate(129.0001, 35.1199),
				new Coordinate(129.0001, 35.1201),
				new Coordinate(128.9999, 35.1201),
				new Coordinate(128.9999, 35.1199)
			}));
		when(hazardReportRerouteCustomModelFactory.create(any()))
			.thenReturn(JsonNodeFactory.instance.objectNode());
		when(walkRouteUserProfileQueryService.getProfile(userId))
			.thenReturn(new WalkRouteUserProfile(PrimaryUserType.LOW_VISION, null));
		when(walkRouteProfileService.resolve(PrimaryUserType.LOW_VISION, null, RouteOption.SAFE))
			.thenReturn(WalkRouteProfile.VISUAL_SAFE);
		when(graphHopperRouteClient.routeWithCustomModel(any(), any()))
			.thenReturn(reroutedPath);
		when(walkRoutePayloadService.toRouteSummary(any(), any()))
			.thenReturn(reroutedRoute);

		HazardReportRerouteResponse response = service.reroute(
			userId,
			12L,
			new HazardReportRerouteRequest("rr_active_123", new GeoPointRequest(35.1, 129.1), null));

		assertThat(response.rerouted()).isTrue();
		assertThat(response.route()).isNotNull();
		verify(routeSessionCommandService).saveActiveSessionIfAbsent(
			eq(userId),
			eq(response.route().routeId()),
			argThat(point -> point.getY() == 35.1 && point.getX() == 129.1),
			eq(routeSession.getEndPoint()),
			argThat(snapshot -> response.route().routeId().equals(snapshot.get("routeId").asText())));
	}

	@Test
	@DisplayName("reroute returns rerouted false when the request-local avoid area yields no route")
	void returnsNoAlternateRouteWhenGraphHopperCannotRoute() {
		UUID userId = UUID.randomUUID();
		HazardReport report = report(userId, 12L, 35.1200, 129.0000);
		RouteSession routeSession = routeSession("rr_active_123");
		when(hazardReportRepository.findWithImagesByReportId(12L)).thenReturn(Optional.of(report));
		when(routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(
			eq(userId),
			eq("rr_active_123"),
			eq(RouteSessionStatus.ACTIVE)))
			.thenReturn(Optional.of(routeSession));
		when(routeProjectionGeometryService.restoreRouteSnapshot(routeSession)).thenReturn(routeSummary("rr_active_123"));
		when(routeProjectionGeometryService.projectRoutePoint(any(RouteSummaryResponse.class), any(Point.class)))
			.thenReturn(new RouteProjectionGeometryService.ProjectedRoutePoint(
				new Coordinate(129.0000, 35.1200),
				0,
				5.0,
				new Coordinate(128.9999, 35.1200),
				new Coordinate(129.0001, 35.1200)));
		when(hazardReportAvoidAreaBuilder.build(any(), any(), any()))
			.thenReturn(GEOMETRY_FACTORY.createPolygon(new Coordinate[] {
				new Coordinate(128.9999, 35.1199),
				new Coordinate(129.0001, 35.1199),
				new Coordinate(129.0001, 35.1201),
				new Coordinate(128.9999, 35.1201),
				new Coordinate(128.9999, 35.1199)
			}));
		when(hazardReportRerouteCustomModelFactory.create(any()))
			.thenReturn(JsonNodeFactory.instance.objectNode());
		when(walkRouteUserProfileQueryService.getProfile(userId))
			.thenReturn(new WalkRouteUserProfile(PrimaryUserType.LOW_VISION, null));
		when(walkRouteProfileService.resolve(PrimaryUserType.LOW_VISION, null, RouteOption.SAFE))
			.thenReturn(WalkRouteProfile.VISUAL_SAFE);
		when(graphHopperRouteClient.routeWithCustomModel(any(), any()))
			.thenThrow(new RouteException(RouteErrorCode.ROUTE_NOT_FOUND));

		HazardReportRerouteResponse response = service.reroute(
			userId,
			12L,
			new HazardReportRerouteRequest("rr_active_123", new GeoPointRequest(35.1, 129.1), null));

		assertThat(response.rerouted()).isFalse();
		assertThat(response.route()).isNull();
	}

	private HazardReport report(UUID userId, Long reportId, double lat, double lng) {
		User user = User.create(SocialProvider.KAKAO, "kakao", PrimaryUserType.LOW_VISION, null);
		ReflectionTestUtils.setField(user, "userId", userId);
		HazardReport report = HazardReport.create(
			user,
			ReportType.RAMP,
			"report",
			geoPointConverter.toPoint(new GeoPointRequest(lat, lng)),
			List.of());
		ReflectionTestUtils.setField(report, "reportId", reportId);
		ReflectionTestUtils.setField(report, "status", ReportStatus.PENDING);
		return report;
	}

	private RouteSession routeSession(String routeId) {
		User user = User.create(SocialProvider.KAKAO, "kakao", PrimaryUserType.LOW_VISION, null);
		return RouteSession.create(
			user,
			routeId,
			GEOMETRY_FACTORY.createPoint(new Coordinate(128.9999, 35.1200)),
			GEOMETRY_FACTORY.createPoint(new Coordinate(129.0100, 35.1200)),
			objectMapper.valueToTree(routeSummary(routeId)));
	}

	private RouteSession routeSession(RouteSummaryResponse route) {
		User user = User.create(SocialProvider.KAKAO, "kakao", PrimaryUserType.LOW_VISION, null);
		return RouteSession.create(
			user,
			route.routeId(),
			GEOMETRY_FACTORY.createPoint(new Coordinate(128.9999, 35.1200)),
			GEOMETRY_FACTORY.createPoint(new Coordinate(129.0100, 35.1200)),
			objectMapper.valueToTree(route));
	}

	private RouteSummaryResponse routeSummary(String routeId) {
		return routeSummary(routeId, RouteOption.SAFE);
	}

	private RouteSummaryResponse routeSummary(String routeId, RouteOption routeOption) {
		return new RouteSummaryResponse(
			routeId,
			TransportMode.WALK,
			routeOption,
			List.of(routeOption),
			"safe route",
			BigDecimal.valueOf(120),
			90,
			2,
			List.of(),
			List.of(),
			"LINESTRING(128.9999 35.1200, 129.0001 35.1200, 129.0100 35.1200)",
			List.of(new RouteLegResponse(
				1,
				TransportMode.WALK,
				RouteLegRole.WALK_ONLY,
				"Walk",
				BigDecimal.valueOf(120),
				90,
				2,
				"LINESTRING(128.9999 35.1200, 129.0001 35.1200, 129.0100 35.1200)",
				List.of())));
	}

	private RouteSummaryResponse transitRouteSummary(String routeId, RouteLegRole activeWalkRole) {
		RouteLegResponse firstWalkLeg = new RouteLegResponse(
			1,
			TransportMode.WALK,
			RouteLegRole.WALK_TO_TRANSIT,
			"Walk to boarding stop",
			BigDecimal.valueOf(120),
			90,
			2,
			"LINESTRING(129.0000 35.1200, 129.0004 35.1204)",
			List.of());
		RouteLegResponse transitLeg = new RouteLegResponse(
			2,
			TransportMode.BUS,
			RouteLegRole.TRANSIT,
			"Ride bus",
			BigDecimal.valueOf(700),
			420,
			7,
			"LINESTRING(129.0004 35.1204, 129.0010 35.1210)",
			List.of(),
			"100",
			List.of(),
			new RouteStopResponse("Boarding Stop", BigDecimal.valueOf(35.1204), BigDecimal.valueOf(129.0004)),
			new RouteStopResponse("Alighting Stop", BigDecimal.valueOf(35.1210), BigDecimal.valueOf(129.0010)),
			Boolean.FALSE,
			List.of());
		RouteLegResponse finalWalkLeg = new RouteLegResponse(
			3,
			TransportMode.WALK,
			RouteLegRole.WALK_TO_DESTINATION,
			"Walk to destination",
			BigDecimal.valueOf(180),
			120,
			2,
			"LINESTRING(129.0010 35.1210, 129.0014 35.1214)",
			List.of());
		return new RouteSummaryResponse(
			routeId,
			TransportMode.PUBLIC_TRANSIT,
			RouteOption.RECOMMENDED,
			List.of(RouteOption.RECOMMENDED),
			"Transit route",
			BigDecimal.valueOf(1000),
			630,
			11,
			List.of(),
			List.of(),
			"LINESTRING(129.0000 35.1200, 129.0004 35.1204, 129.0010 35.1210, 129.0014 35.1214)",
			List.of(firstWalkLeg, transitLeg, finalWalkLeg));
	}

	private RouteSummaryResponse transitRouteSummaryWithGuidance(String routeId) {
		RouteLegResponse firstWalkLeg = new RouteLegResponse(
			1,
			TransportMode.WALK,
			RouteLegRole.WALK_TO_TRANSIT,
			"Walk to boarding stop",
			BigDecimal.valueOf(120),
			90,
			2,
			"LINESTRING(129.0000 35.1200, 129.0004 35.1204)",
			List.of(new RouteGuidanceEventResponse(
				1,
				RouteGuidanceEventType.CROSSWALK,
				BigDecimal.valueOf(30),
				20,
				"POINT(129.0002 35.1202)")));
		RouteLegResponse transitLeg = new RouteLegResponse(
			2,
			TransportMode.BUS,
			RouteLegRole.TRANSIT,
			"Ride bus",
			BigDecimal.valueOf(700),
			420,
			7,
			"LINESTRING(129.0004 35.1204, 129.0010 35.1210)",
			List.of(new RouteGuidanceEventResponse(
				1,
				RouteGuidanceEventType.BUS_STOP,
				BigDecimal.valueOf(60),
				15,
				"POINT(129.0006 35.1206)")),
			"100",
			List.of(),
			new RouteStopResponse("Boarding Stop", BigDecimal.valueOf(35.1204), BigDecimal.valueOf(129.0004)),
			new RouteStopResponse("Alighting Stop", BigDecimal.valueOf(35.1210), BigDecimal.valueOf(129.0010)),
			Boolean.FALSE,
			List.of());
		RouteLegResponse finalWalkLeg = new RouteLegResponse(
			3,
			TransportMode.WALK,
			RouteLegRole.WALK_TO_DESTINATION,
			"Walk to destination",
			BigDecimal.valueOf(180),
			120,
			2,
			"LINESTRING(129.0010 35.1210, 129.0014 35.1214)",
			List.of(new RouteGuidanceEventResponse(
				1,
				RouteGuidanceEventType.DESTINATION,
				BigDecimal.valueOf(50),
				35,
				"POINT(129.0012 35.1212)")));
		return new RouteSummaryResponse(
			routeId,
			TransportMode.PUBLIC_TRANSIT,
			RouteOption.RECOMMENDED,
			List.of(RouteOption.RECOMMENDED),
			"Transit route",
			BigDecimal.valueOf(1000),
			630,
			11,
			List.of(),
			List.of(),
			"LINESTRING(129.0000 35.1200, 129.0004 35.1204, 129.0010 35.1210, 129.0014 35.1214)",
			List.of(firstWalkLeg, transitLeg, finalWalkLeg));
	}

	private RouteProjectionGeometryService.ProjectedRoutePoint projectedLegPoint(
		int segmentIndex,
		Coordinate projected,
		Coordinate segmentStart,
		Coordinate segmentEnd) {
		return new RouteProjectionGeometryService.ProjectedRoutePoint(
			projected,
			segmentIndex,
			0.0,
			segmentStart,
			segmentEnd);
	}
}
