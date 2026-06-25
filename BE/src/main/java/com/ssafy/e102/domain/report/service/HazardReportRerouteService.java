package com.ssafy.e102.domain.report.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.e102.domain.report.dto.request.HazardReportRerouteRequest;
import com.ssafy.e102.domain.report.dto.response.HazardReportRerouteResponse;
import com.ssafy.e102.domain.report.entity.HazardReport;
import com.ssafy.e102.domain.report.exception.HazardReportErrorCode;
import com.ssafy.e102.domain.report.exception.HazardReportException;
import com.ssafy.e102.domain.report.repository.HazardReportRepository;
import com.ssafy.e102.domain.route.dto.response.RouteGuidanceEventResponse;
import com.ssafy.e102.domain.route.dto.response.RouteGuidanceEventType;
import com.ssafy.e102.domain.route.dto.response.RouteLegResponse;
import com.ssafy.e102.domain.route.dto.response.RouteStopResponse;
import com.ssafy.e102.domain.route.dto.response.RouteSummaryResponse;
import com.ssafy.e102.domain.route.entity.RouteSession;
import com.ssafy.e102.domain.route.exception.RouteErrorCode;
import com.ssafy.e102.domain.route.exception.RouteException;
import com.ssafy.e102.domain.route.repository.RouteSessionRepository;
import com.ssafy.e102.domain.route.service.RouteProjectionGeometryService;
import com.ssafy.e102.domain.route.service.RouteSessionCommandService;
import com.ssafy.e102.domain.route.service.WalkRouteCandidate;
import com.ssafy.e102.domain.route.service.WalkRoutePayloadService;
import com.ssafy.e102.domain.route.service.WalkRouteProfileService;
import com.ssafy.e102.domain.route.service.WalkRouteUserProfile;
import com.ssafy.e102.domain.route.service.WalkRouteUserProfileQueryService;
import com.ssafy.e102.domain.route.type.RouteBadge;
import com.ssafy.e102.domain.route.type.RouteLegRole;
import com.ssafy.e102.domain.route.type.RouteOption;
import com.ssafy.e102.domain.route.type.RouteSessionStatus;
import com.ssafy.e102.domain.route.type.TransportMode;
import com.ssafy.e102.domain.route.type.WalkRouteProfile;
import com.ssafy.e102.global.external.graphhopper.GraphHopperRouteClient;
import com.ssafy.e102.global.external.graphhopper.GraphHopperRoutePath;
import com.ssafy.e102.global.external.graphhopper.GraphHopperRouteRequest;
import com.ssafy.e102.global.geo.GeoDistanceCalculator;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

@Service
public class HazardReportRerouteService {

	private static final int SRID = 4326;

	private final HazardReportRepository hazardReportRepository;
	private final RouteSessionRepository routeSessionRepository;
	private final RouteSessionCommandService routeSessionCommandService;
	private final RouteProjectionGeometryService routeProjectionGeometryService;
	private final HazardReportAvoidAreaBuilder hazardReportAvoidAreaBuilder;
	private final HazardReportRerouteCustomModelFactory hazardReportRerouteCustomModelFactory;
	private final GraphHopperRouteClient graphHopperRouteClient;
	private final WalkRoutePayloadService walkRoutePayloadService;
	private final WalkRouteUserProfileQueryService walkRouteUserProfileQueryService;
	private final WalkRouteProfileService walkRouteProfileService;
	private final ObjectMapper objectMapper;
	private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), SRID);

	public HazardReportRerouteService(
		HazardReportRepository hazardReportRepository,
		RouteSessionRepository routeSessionRepository,
		RouteSessionCommandService routeSessionCommandService,
		RouteProjectionGeometryService routeProjectionGeometryService,
		HazardReportAvoidAreaBuilder hazardReportAvoidAreaBuilder,
		HazardReportRerouteCustomModelFactory hazardReportRerouteCustomModelFactory,
		GraphHopperRouteClient graphHopperRouteClient,
		WalkRoutePayloadService walkRoutePayloadService,
		WalkRouteUserProfileQueryService walkRouteUserProfileQueryService,
		WalkRouteProfileService walkRouteProfileService,
		ObjectMapper objectMapper) {
		this.hazardReportRepository = hazardReportRepository;
		this.routeSessionRepository = routeSessionRepository;
		this.routeSessionCommandService = routeSessionCommandService;
		this.routeProjectionGeometryService = routeProjectionGeometryService;
		this.hazardReportAvoidAreaBuilder = hazardReportAvoidAreaBuilder;
		this.hazardReportRerouteCustomModelFactory = hazardReportRerouteCustomModelFactory;
		this.graphHopperRouteClient = graphHopperRouteClient;
		this.walkRoutePayloadService = walkRoutePayloadService;
		this.walkRouteUserProfileQueryService = walkRouteUserProfileQueryService;
		this.walkRouteProfileService = walkRouteProfileService;
		this.objectMapper = objectMapper;
	}

	public HazardReportRerouteResponse reroute(UUID userId, Long reportId, HazardReportRerouteRequest request) {
		routeProjectionGeometryService.validateCurrentPoint(request.currentPoint());
		HazardReport hazardReport = hazardReportRepository.findWithImagesByReportId(reportId)
			.orElseThrow(() -> new HazardReportException(HazardReportErrorCode.HAZARD_REPORT_NOT_FOUND));
		if (!hazardReport.isOwner(userId)) {
			throw new HazardReportException(HazardReportErrorCode.HAZARD_REPORT_FORBIDDEN);
		}

		RouteSession routeSession = routeSessionRepository
			.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(
				userId,
				request.routeId(),
				RouteSessionStatus.ACTIVE)
			.orElseThrow(() -> new RouteException(RouteErrorCode.ROUTE_SESSION_NOT_FOUND));

		RouteSummaryResponse currentRoute = routeProjectionGeometryService.restoreRouteSnapshot(routeSession);
		if (currentRoute.transportMode() == TransportMode.WALK) {
			return rerouteWalkRoute(userId, request, hazardReport, routeSession, currentRoute);
		}
		if (currentRoute.transportMode() == TransportMode.PUBLIC_TRANSIT) {
			return rerouteTransitWalkRoute(userId, request, hazardReport, routeSession, currentRoute);
		}
		return new HazardReportRerouteResponse(false, null);
	}

	private HazardReportRerouteResponse rerouteWalkRoute(
		UUID userId,
		HazardReportRerouteRequest request,
		HazardReport hazardReport,
		RouteSession routeSession,
		RouteSummaryResponse currentRoute) {
		WalkRouteProfile profile = resolveWalkRouteProfile(userId, currentRoute.routeOption());
		RouteProjectionGeometryService.ProjectedRoutePoint reportProjection =
			routeProjectionGeometryService.projectRoutePoint(currentRoute, hazardReport.getReportPoint());
		Polygon avoidArea = buildAvoidArea(reportProjection);

		try {
			GraphHopperRoutePath reroutedPath = graphHopperRouteClient.routeWithCustomModel(
				new GraphHopperRouteRequest(
					request.currentPoint(),
					endPoint(routeSession),
					profile),
				hazardReportRerouteCustomModelFactory.create(avoidArea));
			RouteSummaryResponse reroutedRoute = walkRoutePayloadService.toRouteSummary(
				request.routeId(),
				new WalkRouteCandidate(
					currentRoute.routeOption(),
					profile,
					reroutedPath));
			RouteSummaryResponse reroutedRouteWithId = withRouteId(reroutedRoute, newRouteId(request.routeId()));
			saveReroutedSession(userId, request.currentPoint(), routeSession, reroutedRouteWithId);
			return new HazardReportRerouteResponse(true, reroutedRouteWithId);
		} catch (RouteException exception) {
			if (exception.getErrorCode() == RouteErrorCode.ROUTE_NOT_FOUND) {
				return new HazardReportRerouteResponse(false, null);
			}
			throw exception;
		}
	}

	private HazardReportRerouteResponse rerouteTransitWalkRoute(
		UUID userId,
		HazardReportRerouteRequest request,
		HazardReport hazardReport,
		RouteSession routeSession,
		RouteSummaryResponse currentRoute) {
		TransitWalkLegTarget target = resolveTransitWalkLegTarget(currentRoute, routeSession, request);
		if (target == null) {
			return new HazardReportRerouteResponse(false, null);
		}
		WalkRouteProfile profile = resolveWalkRouteProfile(userId, RouteOption.SAFE);
		RouteProjectionGeometryService.ProjectedRoutePoint reportProjection =
			routeProjectionGeometryService.projectRoutePoint(singleLegRoute(currentRoute, target.leg()), hazardReport.getReportPoint());
		Polygon avoidArea = buildAvoidArea(reportProjection);

		try {
			GraphHopperRoutePath reroutedPath = graphHopperRouteClient.routeWithCustomModel(
				new GraphHopperRouteRequest(
					request.currentPoint(),
					target.destination(),
					profile),
				hazardReportRerouteCustomModelFactory.create(avoidArea));
			RouteLegResponse reroutedWalkLeg = walkRoutePayloadService.toWalkLeg(
				target.leg().sequence(),
				target.leg().role(),
				resolveWalkInstruction(target.leg()),
				reroutedPath,
				profile,
				null,
				target.destinationEventType());
			RouteSummaryResponse reroutedRoute = rebuildTransitRoute(currentRoute, target.leg().sequence(), reroutedWalkLeg);
			RouteSummaryResponse reroutedRouteWithId = withRouteId(reroutedRoute, newRouteId(request.routeId()));
			saveReroutedSession(userId, request.currentPoint(), routeSession, reroutedRouteWithId);
			return new HazardReportRerouteResponse(true, reroutedRouteWithId);
		} catch (RouteException exception) {
			if (exception.getErrorCode() == RouteErrorCode.ROUTE_NOT_FOUND) {
				return new HazardReportRerouteResponse(false, null);
			}
			throw exception;
		}
	}

	private WalkRouteProfile resolveWalkRouteProfile(UUID userId, RouteOption routeOption) {
		WalkRouteUserProfile userProfile = walkRouteUserProfileQueryService.getProfile(userId);
		return walkRouteProfileService.resolve(
			userProfile.primaryUserType(),
			userProfile.mobilitySubtype(),
			routeOption);
	}

	private Polygon buildAvoidArea(RouteProjectionGeometryService.ProjectedRoutePoint reportProjection) {
		return hazardReportAvoidAreaBuilder.build(
			reportProjection.projectedCoordinate(),
			reportProjection.segmentStart(),
			reportProjection.segmentEnd());
	}

	private void saveReroutedSession(
		UUID userId,
		GeoPointRequest currentPoint,
		RouteSession routeSession,
		RouteSummaryResponse reroutedRoute) {
		routeSessionCommandService.saveActiveSessionIfAbsent(
			userId,
			reroutedRoute.routeId(),
			toPoint(currentPoint),
			routeSession.getEndPoint(),
			objectMapper.valueToTree(reroutedRoute));
	}

	private TransitWalkLegTarget resolveTransitWalkLegTarget(
		RouteSummaryResponse currentRoute,
		RouteSession routeSession,
		HazardReportRerouteRequest request) {
		RouteLegResponse activeLeg = resolveActiveLeg(currentRoute, request);
		if (activeLeg == null || activeLeg.type() != TransportMode.WALK) {
			return null;
		}
		if (activeLeg.role() == RouteLegRole.WALK_TO_TRANSIT) {
			RouteLegResponse nextTransitLeg = nextTransitLeg(currentRoute, activeLeg.sequence());
			if (nextTransitLeg == null || nextTransitLeg.boardingStop() == null) {
				throw new RouteException(RouteErrorCode.ROUTE_SESSION_NOT_FOUND);
			}
			return new TransitWalkLegTarget(
				activeLeg,
				toGeoPoint(nextTransitLeg.boardingStop()),
				nextTransitLeg.type() == TransportMode.BUS
					? RouteGuidanceEventType.BUS_STOP
					: RouteGuidanceEventType.SUBWAY_ELEVATOR);
		}
		if (activeLeg.role() == RouteLegRole.WALK_TO_DESTINATION || activeLeg.role() == RouteLegRole.TRANSIT_TO_WALK) {
			return new TransitWalkLegTarget(activeLeg, endPoint(routeSession), RouteGuidanceEventType.DESTINATION);
		}
		return null;
	}

	private RouteLegResponse resolveActiveLeg(RouteSummaryResponse currentRoute, HazardReportRerouteRequest request) {
		if (request.activeLegSequence() != null) {
			return currentRoute.legs()
				.stream()
				.filter(leg -> leg.sequence() == request.activeLegSequence())
				.findFirst()
				.orElse(null);
		}

		RouteLegResponse nearestLeg = null;
		double nearestDistanceMeter = Double.MAX_VALUE;
		for (RouteLegResponse leg : currentRoute.legs()) {
			if (leg.geometry() == null || leg.geometry().isBlank()) {
				continue;
			}
			RouteProjectionGeometryService.ProjectedRoutePoint projection =
				routeProjectionGeometryService.projectRoutePoint(singleLegRoute(currentRoute, leg), request.currentPoint());
			double distanceMeter = GeoDistanceCalculator.distanceMeter(
				request.currentPoint().lat(),
				request.currentPoint().lng(),
				projection.projectedCoordinate().y,
				projection.projectedCoordinate().x);
			if (distanceMeter < nearestDistanceMeter) {
				nearestDistanceMeter = distanceMeter;
				nearestLeg = leg;
			}
		}
		return nearestLeg;
	}

	private RouteLegResponse nextTransitLeg(RouteSummaryResponse currentRoute, int currentSequence) {
		return currentRoute.legs()
			.stream()
			.filter(leg -> leg.sequence() > currentSequence)
			.filter(leg -> leg.type() == TransportMode.BUS || leg.type() == TransportMode.SUBWAY)
			.findFirst()
			.orElse(null);
	}

	private String resolveWalkInstruction(RouteLegResponse leg) {
		if (leg.instruction() != null && !leg.instruction().isBlank()) {
			return leg.instruction();
		}
		return leg.role() == RouteLegRole.WALK_TO_TRANSIT ? "환승 지점까지 도보 이동" : "목적지까지 도보 이동";
	}

	private RouteSummaryResponse singleLegRoute(RouteSummaryResponse currentRoute, RouteLegResponse leg) {
		return new RouteSummaryResponse(
			currentRoute.routeId(),
			currentRoute.transportMode(),
			currentRoute.routeOption(),
			currentRoute.routeOptions(),
			currentRoute.title(),
			leg.distanceMeter(),
			leg.durationSecond(),
			leg.estimatedTimeMinute(),
			leg.badges(),
			currentRoute.warnings(),
			leg.geometry(),
			List.of(leg));
	}

	private RouteSummaryResponse rebuildTransitRoute(
		RouteSummaryResponse currentRoute,
		int targetLegSequence,
		RouteLegResponse reroutedWalkLeg) {
		List<RouteLegResponse> rebuiltLegs = new ArrayList<>();
		for (RouteLegResponse leg : currentRoute.legs()) {
			rebuiltLegs.add(leg.sequence() == targetLegSequence ? reroutedWalkLeg : leg);
		}
		List<RouteLegResponse> offsetLegs = withRouteGuidanceOffsets(rebuiltLegs);
		int totalDurationSecond = totalDurationSecond(offsetLegs);
		return new RouteSummaryResponse(
			currentRoute.routeId(),
			currentRoute.transportMode(),
			currentRoute.routeOption(),
			currentRoute.routeOptions(),
			currentRoute.title(),
			totalDistanceMeter(offsetLegs),
			totalDurationSecond,
			estimatedMinute(totalDurationSecond),
			routeBadges(offsetLegs),
			currentRoute.warnings(),
			mergeGeometry(offsetLegs),
			offsetLegs);
	}

	private List<RouteLegResponse> withRouteGuidanceOffsets(List<RouteLegResponse> legs) {
		List<RouteLegResponse> offsetLegs = new ArrayList<>();
		BigDecimal distanceOffset = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
		int durationOffset = 0;
		for (RouteLegResponse leg : legs) {
			offsetLegs.add(withRouteGuidanceOffsets(leg, distanceOffset, durationOffset));
			if (leg.distanceMeter() != null) {
				distanceOffset = distanceOffset.add(leg.distanceMeter()).setScale(2, RoundingMode.HALF_UP);
			}
			durationOffset += leg.durationSecond();
		}
		return List.copyOf(offsetLegs);
	}

	private RouteLegResponse withRouteGuidanceOffsets(
		RouteLegResponse leg,
		BigDecimal distanceOffset,
		int durationOffset) {
		List<RouteGuidanceEventResponse> guidanceEvents = leg.guidanceEvents() == null
			? List.of()
			: leg.guidanceEvents()
				.stream()
				.map(event -> new RouteGuidanceEventResponse(
					event.sequence(),
					event.type(),
					event.direction(),
					event.features(),
					event.distanceFromLegStartMeter(),
					event.durationFromLegStartSecond(),
					distanceOffset.add(event.distanceFromLegStartMeter()).setScale(2, RoundingMode.HALF_UP),
					durationOffset + event.durationFromLegStartSecond(),
					event.geometry()))
				.toList();
		return new RouteLegResponse(
			leg.sequence(),
			leg.type(),
			leg.role(),
			leg.instruction(),
			leg.distanceMeter(),
			leg.durationSecond(),
			leg.estimatedTimeMinute(),
			leg.geometry(),
			guidanceEvents,
			leg.routeNo(),
			leg.laneOptions(),
			leg.boardingStop(),
			leg.arrivingStop(),
			leg.remainingMinute(),
			leg.headsign(),
			leg.isLowFloor(),
			leg.badges());
	}

	private BigDecimal totalDistanceMeter(List<RouteLegResponse> legs) {
		return legs.stream()
			.map(RouteLegResponse::distanceMeter)
			.filter(Objects::nonNull)
			.reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add)
			.setScale(2, RoundingMode.HALF_UP);
	}

	private int totalDurationSecond(List<RouteLegResponse> legs) {
		return legs.stream().mapToInt(RouteLegResponse::durationSecond).sum();
	}

	private int estimatedMinute(int durationSecond) {
		if (durationSecond <= 0) {
			return 0;
		}
		return Math.max(1, durationSecond / 60);
	}

	private List<RouteBadge> routeBadges(List<RouteLegResponse> legs) {
		LinkedHashSet<RouteBadge> badges = new LinkedHashSet<>();
		for (RouteLegResponse leg : legs) {
			if (leg.badges() != null) {
				badges.addAll(leg.badges());
			}
		}
		return List.copyOf(badges);
	}

	private String mergeGeometry(List<RouteLegResponse> legs) {
		List<String> coordinates = new ArrayList<>();
		for (RouteLegResponse leg : legs) {
			coordinates.addAll(extractCoordinates(leg.geometry()));
		}
		if (coordinates.size() < 2) {
			throw new RouteException(RouteErrorCode.ROUTE_SESSION_NOT_FOUND);
		}
		List<String> merged = new ArrayList<>();
		for (String coordinate : coordinates) {
			if (merged.isEmpty() || !merged.get(merged.size() - 1).equals(coordinate)) {
				merged.add(coordinate);
			}
		}
		return "LINESTRING(" + String.join(", ", merged) + ")";
	}

	private List<String> extractCoordinates(String lineString) {
		if (lineString == null || !lineString.startsWith("LINESTRING(")) {
			return List.of();
		}
		return List.of(lineString.substring("LINESTRING(".length(), lineString.length() - 1).split(", "));
	}

	private GeoPointRequest endPoint(RouteSession routeSession) {
		Point endPoint = routeSession.getEndPoint();
		if (endPoint == null) {
			throw new RouteException(RouteErrorCode.ROUTE_SESSION_NOT_FOUND);
		}
		return new GeoPointRequest(endPoint.getY(), endPoint.getX());
	}

	private GeoPointRequest toGeoPoint(RouteStopResponse stop) {
		if (stop == null || stop.lat() == null || stop.lng() == null) {
			throw new RouteException(RouteErrorCode.ROUTE_SESSION_NOT_FOUND);
		}
		return new GeoPointRequest(stop.lat().doubleValue(), stop.lng().doubleValue());
	}

	private Point toPoint(GeoPointRequest pointRequest) {
		Point point = geometryFactory.createPoint(new Coordinate(pointRequest.lng(), pointRequest.lat()));
		point.setSRID(SRID);
		return point;
	}

	private RouteSummaryResponse withRouteId(RouteSummaryResponse route, String routeId) {
		return new RouteSummaryResponse(
			routeId,
			route.transportMode(),
			route.routeOption(),
			route.routeOptions(),
			route.title(),
			route.distanceMeter(),
			route.durationSecond(),
			route.estimatedTimeMinute(),
			route.badges(),
			route.warnings(),
			route.geometry(),
			route.legs());
	}

	private String newRouteId(String currentRouteId) {
		return currentRouteId + "_hazard_" + UUID.randomUUID();
	}

	private record TransitWalkLegTarget(
		RouteLegResponse leg,
		GeoPointRequest destination,
		RouteGuidanceEventType destinationEventType) {
	}
}
