package com.ssafy.e102.domain.route.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

@Service
public class RerouteService {

	private static final double BUSAN_MIN_LAT = 34.85;
	private static final double BUSAN_MAX_LAT = 35.45;
	private static final double BUSAN_MIN_LNG = 128.70;
	private static final double BUSAN_MAX_LNG = 129.40;
	private static final double EARTH_RADIUS_METER = 6_371_000.0;
	private static final double NO_REROUTE_DISTANCE_METER = 10.0;
	private static final double FULL_REROUTE_MAX_DISTANCE_METER = 500.0;
	private static final int SRID = 4326;

	private final RouteSessionRepository routeSessionRepository;
	private final RouteSessionCommandService routeSessionCommandService;
	private final ObjectMapper objectMapper;
	private final WalkRouteSearchService walkRouteSearchService;
	private final TransitRouteSearchService transitRouteSearchService;
	private final WKTReader wktReader = new WKTReader();
	private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), SRID);

	public RerouteService(
		RouteSessionRepository routeSessionRepository,
		RouteSessionCommandService routeSessionCommandService,
		ObjectMapper objectMapper,
		WalkRouteSearchService walkRouteSearchService,
		TransitRouteSearchService transitRouteSearchService) {
		this.routeSessionRepository = routeSessionRepository;
		this.routeSessionCommandService = routeSessionCommandService;
		this.objectMapper = objectMapper;
		this.walkRouteSearchService = walkRouteSearchService;
		this.transitRouteSearchService = transitRouteSearchService;
	}

	public RerouteResponse reroute(UUID userId, RerouteRequest request) {
		validateRequest(request);
		validateCurrentPoint(request.currentPoint());
		RouteSession routeSession = getOwnedRouteSession(userId, request.routeId());
		RouteSummaryResponse route = restoreRouteSnapshot(routeSession);
		RouteProjection projection = projectCurrentPoint(route, request.currentPoint());
		if (projection.distanceMeter() <= NO_REROUTE_DISTANCE_METER) {
			return new RerouteResponse(null);
		}
		if (projection.distanceMeter() <= FULL_REROUTE_MAX_DISTANCE_METER) {
			RouteSummaryResponse reroutedRoute = withNewRouteId(
				fullReroute(userId, request.currentPoint(), routeSession, route),
				newRouteId("rr_full"));
			saveRerouteSession(userId, routeSession, request.currentPoint(), reroutedRoute);
			return new RerouteResponse(reroutedRoute);
		}
		throw new RouteException(RouteErrorCode.ROUTE_TOO_FAR_FOR_REROUTE);
	}

	private void saveRerouteSession(
		UUID userId,
		RouteSession previousSession,
		GeoPointRequest currentPoint,
		RouteSummaryResponse route) {
		routeSessionCommandService.saveActiveSessionIfAbsent(
			userId,
			route.routeId(),
			toPoint(currentPoint),
			previousSession.getEndPoint(),
			objectMapper.valueToTree(route));
	}

	private Point toPoint(GeoPointRequest request) {
		Point point = geometryFactory.createPoint(new Coordinate(request.lng(), request.lat()));
		point.setSRID(SRID);
		return point;
	}

	private RouteSummaryResponse withNewRouteId(RouteSummaryResponse route, String routeId) {
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

	private String newRouteId(String prefix) {
		return prefix + "_" + UUID.randomUUID();
	}

	private RouteSummaryResponse fullReroute(
		UUID userId,
		GeoPointRequest currentPoint,
		RouteSession routeSession,
		RouteSummaryResponse previousRoute) {
		WalkRouteSearchRequest searchRequest = new WalkRouteSearchRequest(
			currentPoint,
			endPoint(routeSession));
		WalkRouteSearchResponse response = switch (previousRoute.transportMode()) {
			case WALK -> walkRouteSearchService.search(userId, searchRequest);
			case PUBLIC_TRANSIT, BUS, SUBWAY -> transitRouteSearchService.search(userId, searchRequest);
		};
		return response.routes()
			.stream()
			.filter(route -> route.routeOptions() != null && route.routeOptions().contains(previousRoute.routeOption()))
			.findFirst()
			.or(() -> response.routes().stream().findFirst())
			.orElseThrow(() -> new RouteException(RouteErrorCode.ROUTE_NOT_FOUND));
	}

	private GeoPointRequest endPoint(RouteSession routeSession) {
		Point endPoint = routeSession.getEndPoint();
		if (endPoint == null) {
			throw new RouteException(RouteErrorCode.ROUTE_SESSION_NOT_FOUND);
		}
		return new GeoPointRequest(endPoint.getY(), endPoint.getX());
	}

	private RouteSession getOwnedRouteSession(UUID userId, String routeId) {
		return routeSessionRepository.findFirstByUser_UserIdAndRouteIdOrderByUpdatedAtDesc(userId, routeId)
			.orElseGet(() -> {
				if (routeSessionRepository.findFirstByRouteIdOrderByUpdatedAtDesc(routeId).isPresent()) {
					throw new RouteException(RouteErrorCode.ROUTE_ACCESS_DENIED);
				}
				throw new RouteException(RouteErrorCode.ROUTE_SESSION_NOT_FOUND);
			});
	}

	private RouteSummaryResponse restoreRouteSnapshot(RouteSession routeSession) {
		if (routeSession.getRouteSnapshotJson() == null || routeSession.getRouteSnapshotJson().isNull()) {
			throw new RouteException(RouteErrorCode.ROUTE_SESSION_NOT_FOUND);
		}
		try {
			RouteSummaryResponse route = objectMapper.treeToValue(
				routePayloadSnapshot(routeSession.getRouteSnapshotJson()),
				RouteSummaryResponse.class);
			validateRestoredRoute(route);
			return route;
		} catch (JsonProcessingException | IllegalArgumentException exception) {
			throw new RouteException(RouteErrorCode.ROUTE_SESSION_NOT_FOUND, "선택한 경로 정보를 복구할 수 없습니다.", exception);
		}
	}

	private JsonNode routePayloadSnapshot(JsonNode snapshot) {
		if (snapshot.has("backendMetadata") && snapshot instanceof ObjectNode objectNode) {
			ObjectNode routePayload = objectNode.deepCopy();
			routePayload.remove("backendMetadata");
			return routePayload;
		}
		return snapshot;
	}

	private void validateRestoredRoute(RouteSummaryResponse route) {
		if (route == null || route.routeId() == null || route.routeId().isBlank()
			|| route.transportMode() == null || route.routeOption() == null
			|| route.geometry() == null || route.geometry().isBlank()) {
			throw new RouteException(RouteErrorCode.ROUTE_SESSION_NOT_FOUND, "선택한 경로 정보를 복구할 수 없습니다.");
		}
	}

	private RouteProjection projectCurrentPoint(RouteSummaryResponse route, GeoPointRequest currentPoint) {
		List<LegGeometry> legGeometries = legGeometries(route);
		if (legGeometries.isEmpty()) {
			return nearestProjection(currentPoint, route.geometry(), null);
		}
		RouteProjection nearestProjection = null;
		for (LegGeometry legGeometry : legGeometries) {
			RouteProjection projection = nearestProjection(currentPoint, legGeometry.geometry(),
				legGeometry.sequence());
			double distanceMeter = projection.distanceMeter();
			if (nearestProjection == null || distanceMeter < nearestProjection.distanceMeter()) {
				nearestProjection = projection;
			}
		}
		return nearestProjection;
	}

	private List<LegGeometry> legGeometries(RouteSummaryResponse route) {
		if (route.legs() == null || route.legs().isEmpty()) {
			return List.of();
		}
		List<LegGeometry> legGeometries = new ArrayList<>();
		for (RouteLegResponse leg : route.legs()) {
			if (leg.geometry() != null && !leg.geometry().isBlank()) {
				legGeometries.add(new LegGeometry(leg.sequence(), leg.geometry()));
			}
		}
		return List.copyOf(legGeometries);
	}

	private RouteProjection nearestProjection(GeoPointRequest point, String wkt, Integer legSequence) {
		Geometry geometry = parseGeometry(wkt);
		Coordinate[] coordinates = geometry.getCoordinates();
		if (coordinates.length == 0) {
			throw new RouteException(RouteErrorCode.ROUTE_SESSION_NOT_FOUND, "선택한 경로 정보를 복구할 수 없습니다.");
		}
		if (coordinates.length == 1) {
			return new RouteProjection(
				distanceMeter(point.lat(), point.lng(), coordinates[0].y, coordinates[0].x),
				legSequence,
				coordinates[0],
				0,
				0.0);
		}
		RouteProjection nearestProjection = null;
		double distanceFromStartMeter = 0.0;
		for (int index = 0; index < coordinates.length - 1; index++) {
			SegmentProjection projection = projectToSegment(point, coordinates[index], coordinates[index + 1]);
			double projectedDistanceFromStart = distanceFromStartMeter
				+ distanceMeter(
					coordinates[index].y,
					coordinates[index].x,
					projection.coordinate().y,
					projection.coordinate().x);
			RouteProjection routeProjection = new RouteProjection(
				projection.distanceMeter(),
				legSequence,
				projection.coordinate(),
				index,
				projectedDistanceFromStart);
			if (nearestProjection == null || routeProjection.distanceMeter() < nearestProjection.distanceMeter()) {
				nearestProjection = routeProjection;
			}
			distanceFromStartMeter += distanceMeter(
				coordinates[index].y,
				coordinates[index].x,
				coordinates[index + 1].y,
				coordinates[index + 1].x);
		}
		return nearestProjection;
	}

	private Geometry parseGeometry(String wkt) {
		try {
			return wktReader.read(wkt);
		} catch (ParseException | IllegalArgumentException exception) {
			throw new RouteException(RouteErrorCode.ROUTE_SESSION_NOT_FOUND, "선택한 경로 정보를 복구할 수 없습니다.", exception);
		}
	}

	private SegmentProjection projectToSegment(GeoPointRequest point, Coordinate start, Coordinate end) {
		double referenceLat = Math.toRadians(point.lat());
		double pointX = toProjectedX(point.lng(), referenceLat);
		double pointY = toProjectedY(point.lat());
		double startX = toProjectedX(start.x, referenceLat);
		double startY = toProjectedY(start.y);
		double endX = toProjectedX(end.x, referenceLat);
		double endY = toProjectedY(end.y);
		double dx = endX - startX;
		double dy = endY - startY;
		double segmentLengthSquared = dx * dx + dy * dy;
		if (segmentLengthSquared == 0.0) {
			return new SegmentProjection(distanceMeter(point.lat(), point.lng(), start.y, start.x), start);
		}
		double t = ((pointX - startX) * dx + (pointY - startY) * dy) / segmentLengthSquared;
		double clampedT = Math.max(0.0, Math.min(1.0, t));
		double projectedX = startX + clampedT * dx;
		double projectedY = startY + clampedT * dy;
		Coordinate projectedCoordinate = new Coordinate(
			start.x + (end.x - start.x) * clampedT,
			start.y + (end.y - start.y) * clampedT);
		return new SegmentProjection(Math.hypot(pointX - projectedX, pointY - projectedY), projectedCoordinate);
	}

	private double toProjectedX(double lng, double referenceLat) {
		return Math.toRadians(lng) * Math.cos(referenceLat) * EARTH_RADIUS_METER;
	}

	private double toProjectedY(double lat) {
		return Math.toRadians(lat) * EARTH_RADIUS_METER;
	}

	private double distanceMeter(double fromLat, double fromLng, double toLat, double toLng) {
		double startLat = Math.toRadians(fromLat);
		double endLat = Math.toRadians(toLat);
		double deltaLat = Math.toRadians(toLat - fromLat);
		double deltaLng = Math.toRadians(toLng - fromLng);
		double haversine = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
			+ Math.cos(startLat) * Math.cos(endLat) * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
		double angularDistance = 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
		return EARTH_RADIUS_METER * angularDistance;
	}

	private record LegGeometry(
		int sequence,
		String geometry) {
	}

	private record RouteProjection(
		double distanceMeter,
		Integer legSequence,
		Coordinate projectedCoordinate,
		int segmentIndex,
		double distanceFromLegStartMeter) {
	}

	private record SegmentProjection(
		double distanceMeter,
		Coordinate coordinate) {
	}

	private void validateRequest(RerouteRequest request) {
		if (request == null || request.routeId() == null || request.routeId().isBlank()
			|| request.currentPoint() == null) {
			throw new RouteException(RouteErrorCode.INVALID_REROUTE_REQUEST);
		}
	}

	private void validateCurrentPoint(GeoPointRequest point) {
		if (point.lat() == null || point.lng() == null
			|| point.lat() < -90.0 || point.lat() > 90.0
			|| point.lng() < -180.0 || point.lng() > 180.0) {
			throw new RouteException(RouteErrorCode.INVALID_CURRENT_POINT);
		}
		if (!isInBusan(point)) {
			throw new RouteException(RouteErrorCode.OUT_OF_SERVICE_AREA);
		}
	}

	private boolean isInBusan(GeoPointRequest point) {
		return point.lat() >= BUSAN_MIN_LAT
			&& point.lat() <= BUSAN_MAX_LAT
			&& point.lng() >= BUSAN_MIN_LNG
			&& point.lng() <= BUSAN_MAX_LNG;
	}
}
