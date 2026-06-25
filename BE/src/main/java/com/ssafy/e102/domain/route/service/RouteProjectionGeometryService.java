package com.ssafy.e102.domain.route.service;

import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.e102.domain.route.dto.response.RouteLegResponse;
import com.ssafy.e102.domain.route.dto.response.RouteSummaryResponse;
import com.ssafy.e102.domain.route.entity.RouteSession;
import com.ssafy.e102.domain.route.exception.RouteErrorCode;
import com.ssafy.e102.domain.route.exception.RouteException;
import com.ssafy.e102.global.geo.GeoDistanceCalculator;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

@Service
public class RouteProjectionGeometryService {

	private static final double BUSAN_MIN_LAT = 34.85;
	private static final double BUSAN_MAX_LAT = 35.45;
	private static final double BUSAN_MIN_LNG = 128.70;
	private static final double BUSAN_MAX_LNG = 129.40;
	private static final double EARTH_RADIUS_METER = 6_371_000.0;

	private final ObjectMapper objectMapper;
	private final WKTReader wktReader = new WKTReader();

	public RouteProjectionGeometryService(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public void validateCurrentPoint(GeoPointRequest point) {
		if (point == null || point.lat() == null || point.lng() == null
			|| point.lat() < -90.0 || point.lat() > 90.0
			|| point.lng() < -180.0 || point.lng() > 180.0) {
			throw new RouteException(RouteErrorCode.INVALID_CURRENT_POINT);
		}
		if (!isInBusan(point)) {
			throw new RouteException(RouteErrorCode.OUT_OF_SERVICE_AREA);
		}
	}

	public RouteSummaryResponse restoreRouteSnapshot(RouteSession routeSession) {
		if (routeSession == null || routeSession.getRouteSnapshotJson() == null || routeSession.getRouteSnapshotJson().isNull()) {
			throw new RouteException(RouteErrorCode.ROUTE_SESSION_NOT_FOUND);
		}
		try {
			RouteSummaryResponse route = objectMapper.treeToValue(
				routePayloadSnapshot(routeSession.getRouteSnapshotJson()),
				RouteSummaryResponse.class);
			validateRestoredRoute(route);
			return route;
		} catch (JsonProcessingException | IllegalArgumentException exception) {
			throw new RouteException(RouteErrorCode.ROUTE_SESSION_NOT_FOUND, "Failed to restore route snapshot.", exception);
		}
	}

	public ProjectedRoutePoint projectRoutePoint(RouteSummaryResponse route, Point point) {
		if (point == null) {
			throw new RouteException(RouteErrorCode.INVALID_CURRENT_POINT);
		}
		return projectRoutePoint(route, new GeoPointRequest(point.getY(), point.getX()));
	}

	public ProjectedRoutePoint projectRoutePoint(RouteSummaryResponse route, GeoPointRequest point) {
		Geometry geometry = parseGeometry(resolveGeometry(route));
		Coordinate[] coordinates = geometry.getCoordinates();
		if (coordinates.length < 2) {
			throw new RouteException(RouteErrorCode.ROUTE_SESSION_NOT_FOUND, "Route geometry is too short.");
		}
		ProjectedRoutePoint nearest = null;
		double nearestDistanceMeter = Double.MAX_VALUE;
		double distanceFromRouteStartMeter = 0.0;
		for (int index = 0; index < coordinates.length - 1; index++) {
			SegmentProjection projection = projectToSegment(point, coordinates[index], coordinates[index + 1]);
			double projectedDistanceFromStart = distanceFromRouteStartMeter
				+ GeoDistanceCalculator.distanceMeter(
					coordinates[index].y,
					coordinates[index].x,
					projection.coordinate().y,
					projection.coordinate().x);
			if (projection.distanceMeter() < nearestDistanceMeter) {
				nearestDistanceMeter = projection.distanceMeter();
				nearest = new ProjectedRoutePoint(
					projection.coordinate(),
					index,
					projectedDistanceFromStart,
					coordinates[index],
					coordinates[index + 1]);
			}
			distanceFromRouteStartMeter += GeoDistanceCalculator.distanceMeter(
				coordinates[index].y,
				coordinates[index].x,
				coordinates[index + 1].y,
				coordinates[index + 1].x);
		}
		if (nearest == null) {
			throw new RouteException(RouteErrorCode.ROUTE_SESSION_NOT_FOUND, "No projection point exists.");
		}
		return nearest;
	}

	private String resolveGeometry(RouteSummaryResponse route) {
		if (route.geometry() != null && !route.geometry().isBlank()) {
			return route.geometry();
		}
		List<RouteLegResponse> legs = route.legs();
		if (legs != null) {
			for (RouteLegResponse leg : legs) {
				if (leg.geometry() != null && !leg.geometry().isBlank()) {
					return leg.geometry();
				}
			}
		}
		throw new RouteException(RouteErrorCode.ROUTE_SESSION_NOT_FOUND, "Route geometry is missing.");
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
			|| route.transportMode() == null || route.routeOption() == null) {
			throw new RouteException(RouteErrorCode.ROUTE_SESSION_NOT_FOUND, "Route snapshot is incomplete.");
		}
	}

	private Geometry parseGeometry(String wkt) {
		try {
			return wktReader.read(wkt);
		} catch (ParseException | IllegalArgumentException exception) {
			throw new RouteException(RouteErrorCode.ROUTE_SESSION_NOT_FOUND, "Failed to parse route geometry.", exception);
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
			return new SegmentProjection(
				GeoDistanceCalculator.distanceMeter(point.lat(), point.lng(), start.y, start.x),
				start);
		}
		double t = ((pointX - startX) * dx + (pointY - startY) * dy) / segmentLengthSquared;
		double clampedT = Math.max(0.0, Math.min(1.0, t));
		Coordinate projectedCoordinate = new Coordinate(
			start.x + (end.x - start.x) * clampedT,
			start.y + (end.y - start.y) * clampedT);
		return new SegmentProjection(
			GeoDistanceCalculator.distanceMeter(point.lat(), point.lng(), projectedCoordinate.y, projectedCoordinate.x),
			projectedCoordinate);
	}

	private double toProjectedX(double lng, double referenceLat) {
		return Math.toRadians(lng) * Math.cos(referenceLat) * EARTH_RADIUS_METER;
	}

	private double toProjectedY(double lat) {
		return Math.toRadians(lat) * EARTH_RADIUS_METER;
	}

	private boolean isInBusan(GeoPointRequest point) {
		return point.lat() >= BUSAN_MIN_LAT
			&& point.lat() <= BUSAN_MAX_LAT
			&& point.lng() >= BUSAN_MIN_LNG
			&& point.lng() <= BUSAN_MAX_LNG;
	}

	private record SegmentProjection(
		double distanceMeter,
		Coordinate coordinate) {
	}

	public record ProjectedRoutePoint(
		Coordinate projectedCoordinate,
		int segmentIndex,
		double distanceFromRouteStartMeter,
		Coordinate segmentStart,
		Coordinate segmentEnd) {
	}
}
