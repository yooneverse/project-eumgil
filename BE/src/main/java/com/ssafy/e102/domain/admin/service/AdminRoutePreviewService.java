package com.ssafy.e102.domain.admin.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import org.locationtech.jts.geom.Coordinate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.e102.domain.admin.dto.request.AdminRoutePreviewRequest;
import com.ssafy.e102.domain.admin.dto.response.AdminRoutePreviewItemResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminRoutePreviewResponse;
import com.ssafy.e102.domain.admin.type.AdminRouteProfileGroup;
import com.ssafy.e102.domain.route.entity.RoadSegment;
import com.ssafy.e102.domain.route.repository.RoadSegmentRepository;
import com.ssafy.e102.domain.route.repository.SegmentFeatureRepository;
import com.ssafy.e102.domain.route.type.AccessibilityState;
import com.ssafy.e102.domain.route.type.SegmentFeatureType;
import com.ssafy.e102.domain.route.type.SurfaceState;
import com.ssafy.e102.domain.route.type.WalkRouteProfile;
import com.ssafy.e102.domain.route.type.WidthState;
import com.ssafy.e102.global.exception.BusinessException;
import com.ssafy.e102.global.exception.CommonErrorCode;
import com.ssafy.e102.global.geo.dto.GeoPointResponse;

@Service
@Transactional(readOnly = true)
public class AdminRoutePreviewService {

	private static final int ROUTE_GRAPH_LIMIT = 20_000;
	private static final double WALK_SPEED_METER_PER_SECOND = 1.2;
	private static final String ALL_DONG = AdminService.ALL_DONG;

	private final RoadSegmentRepository roadSegmentRepository;
	private final SegmentFeatureRepository segmentFeatureRepository;

	public AdminRoutePreviewService(
		RoadSegmentRepository roadSegmentRepository,
		SegmentFeatureRepository segmentFeatureRepository) {
		this.roadSegmentRepository = roadSegmentRepository;
		this.segmentFeatureRepository = segmentFeatureRepository;
	}

	public AdminRoutePreviewResponse preview(AdminRoutePreviewRequest request) {
		List<RoadSegment> segments = hasAreaScope(request.dong())
			? roadSegmentRepository.findAllIntersectingArea(request.gu(), request.dong(), ROUTE_GRAPH_LIMIT)
			: roadSegmentRepository.findAllIntersectingGu(request.gu(), ROUTE_GRAPH_LIMIT);
		if (segments.isEmpty()) {
			throw new BusinessException(CommonErrorCode.NOT_FOUND, "선택한 범위에 경로 계산 가능한 보행 네트워크가 없습니다.");
		}

		Map<Long, Set<SegmentFeatureType>> featureTypesByEdgeId = loadFeatureTypes(segments);
		RouteGraph graph = buildGraph(segments, featureTypesByEdgeId);
		Long startNodeId = graph.nearestNodeId(request.startPoint()
			.lat(),
			request.startPoint()
				.lng());
		Long endNodeId = graph.nearestNodeId(request.endPoint()
			.lat(),
			request.endPoint()
				.lng());

		return new AdminRoutePreviewResponse(
			findRoute(
				graph,
				startNodeId,
				endNodeId,
				request.profileGroup(),
				request.profileGroup()
					.getSafeProfile(),
				true,
				request),
			findRoute(
				graph,
				startNodeId,
				endNodeId,
				request.profileGroup(),
				request.profileGroup()
					.getFastProfile(),
				false,
				request));
	}

	private Map<Long, Set<SegmentFeatureType>> loadFeatureTypes(List<RoadSegment> segments) {
		Map<Long, Set<SegmentFeatureType>> featureTypesByEdgeId = new HashMap<>();
		segmentFeatureRepository.findByEdgeIdIn(segments.stream()
			.map(RoadSegment::getEdgeId)
			.toList())
			.forEach(feature -> featureTypesByEdgeId.computeIfAbsent(feature.getEdgeId(), ignored -> new HashSet<>())
				.add(feature.getFeatureType()));
		return featureTypesByEdgeId;
	}

	private RouteGraph buildGraph(
		List<RoadSegment> segments,
		Map<Long, Set<SegmentFeatureType>> featureTypesByEdgeId) {
		RouteGraph graph = new RouteGraph();
		for (RoadSegment segment : segments) {
			Coordinate[] coordinates = segment.getGeom()
				.getCoordinates();
			if (coordinates.length < 2) {
				continue;
			}
			List<GeoPointResponse> forwardCoordinates = toGeoPoints(coordinates);
			List<GeoPointResponse> reverseCoordinates = new ArrayList<>(forwardCoordinates);
			Collections.reverse(reverseCoordinates);
			GraphEdge forward = new GraphEdge(
				segment,
				segment.getFromNodeId(),
				segment.getToNodeId(),
				forwardCoordinates,
				featureTypesByEdgeId.getOrDefault(segment.getEdgeId(), Set.of()));
			GraphEdge reverse = new GraphEdge(
				segment,
				segment.getToNodeId(),
				segment.getFromNodeId(),
				reverseCoordinates,
				featureTypesByEdgeId.getOrDefault(segment.getEdgeId(), Set.of()));
			graph.addEdge(forward, coordinates[0], coordinates[coordinates.length - 1]);
			graph.addEdge(reverse, coordinates[coordinates.length - 1], coordinates[0]);
		}
		return graph;
	}

	private AdminRoutePreviewItemResponse findRoute(
		RouteGraph graph,
		Long startNodeId,
		Long endNodeId,
		AdminRouteProfileGroup profileGroup,
		WalkRouteProfile profile,
		boolean safe,
		AdminRoutePreviewRequest request) {
		Map<Long, Double> distances = new HashMap<>();
		Map<Long, GraphEdge> previousEdges = new HashMap<>();
		PriorityQueue<NodeCost> queue = new PriorityQueue<>(Comparator.comparingDouble(NodeCost::cost));
		distances.put(startNodeId, 0.0);
		queue.add(new NodeCost(startNodeId, 0.0));

		while (!queue.isEmpty()) {
			NodeCost current = queue.poll();
			if (current.cost() > distances.getOrDefault(current.nodeId(), Double.MAX_VALUE)) {
				continue;
			}
			if (current.nodeId()
				.equals(endNodeId)) {
				break;
			}
			for (GraphEdge edge : graph.edgesFrom(current.nodeId())) {
				double edgeCost = weightedCost(edge, profileGroup, safe);
				if (!Double.isFinite(edgeCost)) {
					continue;
				}
				double nextCost = current.cost() + edgeCost;
				if (nextCost < distances.getOrDefault(edge.toNodeId(), Double.MAX_VALUE)) {
					distances.put(edge.toNodeId(), nextCost);
					previousEdges.put(edge.toNodeId(), edge);
					queue.add(new NodeCost(edge.toNodeId(), nextCost));
				}
			}
		}

		if (!distances.containsKey(endNodeId)) {
			throw new BusinessException(CommonErrorCode.NOT_FOUND, "선택한 시작점과 도착점 사이의 DB 기반 경로를 찾을 수 없습니다.");
		}

		List<GraphEdge> routeEdges = collectRouteEdges(startNodeId, endNodeId, previousEdges);
		double distanceMeter = routeEdges.stream()
			.mapToDouble(GraphEdge::lengthMeter)
			.sum();
		double weightedDistanceMeter = routeEdges.stream()
			.mapToDouble(edge -> weightedCost(edge, profileGroup, safe))
			.sum();

		return new AdminRoutePreviewItemResponse(
			profile.name(),
			BigDecimal.valueOf(distanceMeter)
				.setScale(2, RoundingMode.HALF_UP),
			(int)Math.ceil(weightedDistanceMeter / WALK_SPEED_METER_PER_SECOND),
			(int)Math.ceil(weightedDistanceMeter / WALK_SPEED_METER_PER_SECOND / 60.0),
			toRouteCoordinates(routeEdges, request));
	}

	private List<GraphEdge> collectRouteEdges(Long startNodeId, Long endNodeId, Map<Long, GraphEdge> previousEdges) {
		ArrayDeque<GraphEdge> routeEdges = new ArrayDeque<>();
		Long cursor = endNodeId;
		while (!cursor.equals(startNodeId)) {
			GraphEdge edge = previousEdges.get(cursor);
			if (edge == null) {
				throw new BusinessException(CommonErrorCode.NOT_FOUND, "선택한 시작점과 도착점 사이의 DB 기반 경로를 찾을 수 없습니다.");
			}
			routeEdges.addFirst(edge);
			cursor = edge.fromNodeId();
		}
		return List.copyOf(routeEdges);
	}

	private List<GeoPointResponse> toRouteCoordinates(List<GraphEdge> routeEdges, AdminRoutePreviewRequest request) {
		List<GeoPointResponse> coordinates = new ArrayList<>();
		coordinates.add(new GeoPointResponse(request.startPoint()
			.lat(),
			request.startPoint()
				.lng()));
		for (GraphEdge edge : routeEdges) {
			for (GeoPointResponse coordinate : edge.coordinates()) {
				if (!coordinates.isEmpty() && samePoint(coordinates.get(coordinates.size() - 1), coordinate)) {
					continue;
				}
				coordinates.add(coordinate);
			}
		}
		GeoPointResponse endPoint = new GeoPointResponse(request.endPoint()
			.lat(),
			request.endPoint()
				.lng());
		if (coordinates.isEmpty() || !samePoint(coordinates.get(coordinates.size() - 1), endPoint)) {
			coordinates.add(endPoint);
		}
		return coordinates;
	}

	private boolean samePoint(GeoPointResponse left, GeoPointResponse right) {
		return Double.compare(left.lat(), right.lat()) == 0 && Double.compare(left.lng(), right.lng()) == 0;
	}

	private List<GeoPointResponse> toGeoPoints(Coordinate[] coordinates) {
		List<GeoPointResponse> points = new ArrayList<>();
		for (Coordinate coordinate : coordinates) {
			points.add(new GeoPointResponse(coordinate.getY(), coordinate.getX()));
		}
		return points;
	}

	private double weightedCost(GraphEdge edge, AdminRouteProfileGroup profileGroup, boolean safe) {
		double factor = 1.0;
		RoadSegment segment = edge.segment();
		Set<SegmentFeatureType> featureTypes = edge.featureTypes();

		if (segment.getWalkAccess() == AccessibilityState.NO) {
			return Double.POSITIVE_INFINITY;
		}
		factor *= unknownFactor(segment.getWalkAccess(), safe ? 1.4 : 1.1);
		if (isWheelchair(profileGroup)) {
			factor *= stairsFactor(segment.getStairsState(), safe ? 100.0 : 30.0, safe ? 1.8 : 1.3);
			if (featureTypes.contains(SegmentFeatureType.STAIRS)) {
				factor *= safe ? 100.0 : 30.0;
			}
			factor *= widthFactor(segment.getWidthState(), safe);
			factor *= surfaceFactor(segment.getSurfaceState(), safe ? 4.0 : 2.0);
		} else if (profileGroup == AdminRouteProfileGroup.VISUAL) {
			factor *= stairsFactor(segment.getStairsState(), safe ? 4.0 : 2.0, safe ? 1.4 : 1.1);
			factor *= visualGuideFactor(segment, featureTypes, safe);
			factor *= surfaceFactor(segment.getSurfaceState(), safe ? 2.0 : 1.3);
		} else {
			factor *= stairsFactor(segment.getStairsState(), safe ? 1.6 : 1.2, 1.05);
			factor *= surfaceFactor(segment.getSurfaceState(), safe ? 1.5 : 1.1);
		}
		factor *= slopeFactor(segment.getAvgSlopePercent(), profileGroup, safe);

		return Math.max(edge.lengthMeter() * factor, 0.1);
	}

	private boolean isWheelchair(AdminRouteProfileGroup profileGroup) {
		return profileGroup == AdminRouteProfileGroup.WHEELCHAIR_MANUAL
			|| profileGroup == AdminRouteProfileGroup.WHEELCHAIR_AUTO;
	}

	private boolean hasAreaScope(String dong) {
		return dong != null && !dong.isBlank() && !ALL_DONG.equals(dong);
	}

	private double unknownFactor(AccessibilityState state, double unknownFactor) {
		if (state == AccessibilityState.UNKNOWN) {
			return unknownFactor;
		}
		return 1.0;
	}

	private double stairsFactor(AccessibilityState state, double yesFactor, double unknownFactor) {
		if (state == AccessibilityState.YES) {
			return yesFactor;
		}
		if (state == AccessibilityState.UNKNOWN) {
			return unknownFactor;
		}
		return 1.0;
	}

	private double slopeFactor(BigDecimal avgSlopePercent, AdminRouteProfileGroup profileGroup, boolean safe) {
		if (avgSlopePercent == null) {
			return 1.0;
		}
		double slope = Math.abs(avgSlopePercent.doubleValue());
		if (isWheelchair(profileGroup)) {
			if (slope >= 8.0) {
				return safe ? 12.0 : 4.0;
			}
			if (slope >= 5.0) {
				return safe ? 4.0 : 2.0;
			}
			if (slope >= 3.0) {
				return safe ? 1.8 : 1.3;
			}
			return 1.0;
		}
		if (slope >= 10.0) {
			return safe ? 3.0 : 1.5;
		}
		if (slope >= 6.0) {
			return safe ? 1.8 : 1.2;
		}
		return 1.0;
	}

	private double widthFactor(WidthState widthState, boolean safe) {
		if (widthState == WidthState.NARROW) {
			return safe ? 8.0 : 3.0;
		}
		if (widthState == WidthState.ADEQUATE_120) {
			return safe ? 1.4 : 1.1;
		}
		if (widthState == WidthState.UNKNOWN) {
			return safe ? 1.5 : 1.2;
		}
		return 1.0;
	}

	private double surfaceFactor(SurfaceState surfaceState, double unpavedFactor) {
		if (surfaceState == SurfaceState.UNPAVED) {
			return unpavedFactor;
		}
		if (surfaceState == SurfaceState.UNKNOWN) {
			return 1.2;
		}
		return 1.0;
	}

	private double visualGuideFactor(RoadSegment segment, Set<SegmentFeatureType> featureTypes, boolean safe) {
		double factor = 1.0;
		if (segment.getBrailleBlockState() == AccessibilityState.YES
			|| featureTypes.contains(SegmentFeatureType.BRAILLE_BLOCK)) {
			factor *= safe ? 0.8 : 0.95;
		} else {
			factor *= safe ? 1.4 : 1.1;
		}
		if (segment.getAudioSignalState() == AccessibilityState.YES
			|| featureTypes.contains(SegmentFeatureType.AUDIO_SIGNAL)) {
			factor *= safe ? 0.85 : 0.95;
		} else if (featureTypes.contains(SegmentFeatureType.CROSSWALK)) {
			factor *= safe ? 1.3 : 1.1;
		}
		return factor;
	}

	private record NodeCost(Long nodeId, double cost) {
	}

	private record GraphEdge(
		RoadSegment segment,
		Long fromNodeId,
		Long toNodeId,
		List<GeoPointResponse> coordinates,
		Set<SegmentFeatureType> featureTypes) {

		private double lengthMeter() {
			return segment.getLengthMeter()
				.doubleValue();
		}
	}

	private static class RouteGraph {

		private final Map<Long, List<GraphEdge>> edgesByNodeId = new HashMap<>();
		private final Map<Long, GeoPointResponse> nodes = new HashMap<>();

		private void addEdge(GraphEdge edge, Coordinate fromCoordinate, Coordinate toCoordinate) {
			edgesByNodeId.computeIfAbsent(edge.fromNodeId(), ignored -> new ArrayList<>())
				.add(edge);
			nodes.putIfAbsent(edge.fromNodeId(), new GeoPointResponse(fromCoordinate.getY(), fromCoordinate.getX()));
			nodes.putIfAbsent(edge.toNodeId(), new GeoPointResponse(toCoordinate.getY(), toCoordinate.getX()));
		}

		private List<GraphEdge> edgesFrom(Long nodeId) {
			return edgesByNodeId.getOrDefault(nodeId, List.of());
		}

		private Long nearestNodeId(double lat, double lng) {
			return nodes.entrySet()
				.stream()
				.min(Comparator.comparingDouble(entry -> distanceMeter(lat, lng, entry.getValue())))
				.map(Map.Entry::getKey)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "경로 계산 가능한 노드를 찾을 수 없습니다."));
		}

		private double distanceMeter(double lat, double lng, GeoPointResponse point) {
			double latMeter = (lat - point.lat()) * 111_320.0;
			double lngMeter = (lng - point.lng()) * 88_800.0;
			return Math.hypot(latMeter, lngMeter);
		}
	}
}
