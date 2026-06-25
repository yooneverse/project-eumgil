package com.ssafy.e102.global.external.graphhopper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * GraphHopper paths[] 원소 중 도보 route payload에 필요한 거리, 시간, geometry를 받는다.
 *
 * <p>points.coordinates는 GeoJSON 순서인 lng/lat 배열이므로 {@link GraphHopperCoordinate}로 변환한다.
 */
public record GraphHopperPathResponse(
	BigDecimal distance,
	long time,
	GraphHopperPointsResponse points,
	@JsonProperty("snapped_waypoints")
	GraphHopperPointsResponse snappedWaypoints,
	Map<String, List<List<JsonNode>>> details) {

	List<GraphHopperCoordinate> coordinates() {
		if (points == null || points.coordinates() == null) {
			return List.of();
		}
		// GraphHopper 좌표 배열은 [lng, lat] 순서라 내부 좌표 record로 명시적으로 옮긴다.
		return points.coordinates()
			.stream()
			.map(GraphHopperCoordinate::from)
			.toList();
	}

	List<GraphHopperCoordinate> snappedCoordinates() {
		if (snappedWaypoints == null || snappedWaypoints.coordinates() == null) {
			return List.of();
		}
		return snappedWaypoints.coordinates()
			.stream()
			.map(GraphHopperCoordinate::from)
			.toList();
	}

	Map<String, List<GraphHopperPathDetail>> pathDetails() {
		if (details == null || details.isEmpty()) {
			return Map.of();
		}
		Map<String, List<GraphHopperPathDetail>> parsed = new LinkedHashMap<>();
		details.forEach((name, ranges) -> parsed.put(name, ranges.stream()
			.filter(range -> range.size() >= 3)
			.map(this::toPathDetail)
			.toList()));
		return parsed;
	}

	private GraphHopperPathDetail toPathDetail(List<JsonNode> range) {
		return new GraphHopperPathDetail(range.get(0).asInt(), range.get(1).asInt(), range.get(2).asText());
	}
}
