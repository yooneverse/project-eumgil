package com.ssafy.e102.domain.admin.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.index.strtree.STRtree;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.e102.domain.admin.dto.request.AdminPlaceAccessibilityFeaturesUpdateRequest;
import com.ssafy.e102.domain.admin.dto.request.AdminPlaceUpdateRequest;
import com.ssafy.e102.domain.admin.dto.request.AdminRoadSegmentAttributesUpdateRequest;
import com.ssafy.e102.domain.admin.dto.response.AdminAreaListResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminAreaBoundaryPropertiesResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminAreaResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminFacilityPayloadResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminFacilityPayloadResponse.AdminFacilitySummaryResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminFacilityPropertiesResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminGeoJsonFeatureCollectionResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminGeoJsonFeatureResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminLineStringGeometryResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminPlaceDetailResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminPointGeometryResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminRoadNetworkBridgePayloadResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminRoadNetworkBridgePropertiesResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminRoadNetworkBridgeSummaryResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminRoadNetworkResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminRoadNetworkSummaryResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminRoadNodePropertiesResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminRoadSegmentPropertiesResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminRoadSegmentUpdateResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminRoutingApplyStatus;
import com.ssafy.e102.domain.admin.repository.AdminAreaRepository;
import com.ssafy.e102.domain.admin.type.AdminAreaAssignmentType;
import com.ssafy.e102.domain.place.entity.Place;
import com.ssafy.e102.domain.place.entity.PlaceAccessibilityFeature;
import com.ssafy.e102.domain.place.exception.PlaceErrorCode;
import com.ssafy.e102.domain.place.exception.PlaceException;
import com.ssafy.e102.domain.place.repository.PlaceAccessibilityFeatureRepository;
import com.ssafy.e102.domain.place.repository.PlaceRepository;
import com.ssafy.e102.domain.place.type.AccessibilityFeatureType;
import com.ssafy.e102.domain.report.entity.HazardReportRouteReviewSegmentDraft;
import com.ssafy.e102.domain.route.entity.RoadNode;
import com.ssafy.e102.domain.route.entity.RoadSegment;
import com.ssafy.e102.domain.route.entity.RoutingSegmentOverride;
import com.ssafy.e102.domain.route.entity.SegmentFeature;
import com.ssafy.e102.domain.route.repository.RoadNodeRepository;
import com.ssafy.e102.domain.route.repository.RoadSegmentRepository;
import com.ssafy.e102.domain.route.repository.RoutingSegmentOverrideRepository;
import com.ssafy.e102.domain.route.repository.SegmentFeatureRepository;
import com.ssafy.e102.domain.route.type.AccessibilityState;
import com.ssafy.e102.domain.route.type.SegmentFeatureType;
import com.ssafy.e102.domain.route.type.SegmentType;
import com.ssafy.e102.domain.route.type.WidthState;
import com.ssafy.e102.global.exception.BusinessException;
import com.ssafy.e102.global.exception.CommonErrorCode;
import com.ssafy.e102.global.geo.GeoPointConverter;

@Service
@Transactional(readOnly = true)
public class AdminMapService {

	private static final Sort ROAD_SEGMENT_SORT = Sort.by(Sort.Direction.ASC, "edgeId");
	private static final Sort PLACE_SORT = Sort.by(Sort.Direction.ASC, "placeId");
	private static final double BRIDGE_MAX_DISTANCE_METER = 50.0;
	private static final double BRIDGE_AUTO_DISTANCE_METER = 12.0;
	private static final int SEGMENT_FEATURE_QUERY_BATCH_SIZE = 1000;
	private static final int ROAD_NODE_QUERY_BATCH_SIZE = 1000;
	private static final String ALL_DONG = AdminService.ALL_DONG;

	private final AdminAreaRepository adminAreaRepository;
	private final ObjectMapper objectMapper;
	private final RoadNodeRepository roadNodeRepository;
	private final RoadSegmentRepository roadSegmentRepository;
	private final SegmentFeatureRepository segmentFeatureRepository;
	private final RoutingSegmentOverrideRepository routingSegmentOverrideRepository;
	private final PlaceRepository placeRepository;
	private final PlaceAccessibilityFeatureRepository placeAccessibilityFeatureRepository;
	private final GeoPointConverter geoPointConverter;
	private final AdminService adminService;
	private final AdminAuditLogService adminAuditLogService;
	private final AdminRoutingApplyService adminRoutingApplyService;
	private final TransactionTemplate transactionTemplate;

	public AdminMapService(
		AdminAreaRepository adminAreaRepository,
		ObjectMapper objectMapper,
		RoadNodeRepository roadNodeRepository,
		RoadSegmentRepository roadSegmentRepository,
		SegmentFeatureRepository segmentFeatureRepository,
		RoutingSegmentOverrideRepository routingSegmentOverrideRepository,
		PlaceRepository placeRepository,
		PlaceAccessibilityFeatureRepository placeAccessibilityFeatureRepository,
		GeoPointConverter geoPointConverter,
		AdminService adminService,
		AdminAuditLogService adminAuditLogService,
		AdminRoutingApplyService adminRoutingApplyService,
		PlatformTransactionManager transactionManager) {
		this.adminAreaRepository = adminAreaRepository;
		this.objectMapper = objectMapper;
		this.roadNodeRepository = roadNodeRepository;
		this.roadSegmentRepository = roadSegmentRepository;
		this.segmentFeatureRepository = segmentFeatureRepository;
		this.routingSegmentOverrideRepository = routingSegmentOverrideRepository;
		this.placeRepository = placeRepository;
		this.placeAccessibilityFeatureRepository = placeAccessibilityFeatureRepository;
		this.geoPointConverter = geoPointConverter;
		this.adminService = adminService;
		this.adminAuditLogService = adminAuditLogService;
		this.adminRoutingApplyService = adminRoutingApplyService;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	public AdminAreaListResponse getAreas() {
		Map<String, Set<String>> dongsByGu = new TreeMap<>();
		adminAreaRepository.findDistinctAreas()
			.forEach(row -> {
				String gu = (String)row[0];
				String dong = (String)row[1];
				dongsByGu.computeIfAbsent(gu, key -> new TreeSet<>())
					.add(dong);
			});

		List<AdminAreaResponse> areas = new ArrayList<>();
		dongsByGu.forEach((gu, dongs) -> {
			Set<String> syntheticDongs = new TreeSet<>();
			dongs.forEach(dong -> {
				String syntheticDong = toSyntheticDong(dong);
				if (!syntheticDong.equals(dong) && !dongs.contains(syntheticDong)) {
					syntheticDongs.add(syntheticDong);
				}
			});
			syntheticDongs.forEach(dong -> areas.add(new AdminAreaResponse(gu, dong)));
			dongs.forEach(dong -> areas.add(new AdminAreaResponse(gu, dong)));
		});
		return new AdminAreaListResponse(areas);
	}

	public AdminRoadNetworkResponse getRoadNetwork(String gu, String dong, int limit) {
		List<RoadSegment> roadSegments;
		long segmentCount;
		if (hasAreaScope(gu, dong)) {
			roadSegments = roadSegmentRepository.findAllIntersectingArea(gu, dong);
			segmentCount = roadSegmentRepository.countIntersectingArea(gu, dong);
		} else if (hasGu(gu)) {
			roadSegments = roadSegmentRepository.findAllIntersectingGu(gu, limit);
			segmentCount = roadSegmentRepository.countIntersectingGu(gu);
		} else {
			Page<RoadSegment> page = roadSegmentRepository.findAll(PageRequest.of(0, limit, ROAD_SEGMENT_SORT));
			roadSegments = page.getContent();
			segmentCount = roadSegmentRepository.count();
		}

		return toRoadNetworkResponse(gu, dong, roadSegments, segmentCount);
	}

	public AdminRoadNetworkResponse getRoadNetwork(
		String gu,
		String dong,
		int limit,
		Double centerLat,
		Double centerLng,
		Integer radiusMeter) {
		if (hasAreaScope(gu, dong) && hasClip(centerLat, centerLng, radiusMeter)) {
			List<RoadSegment> roadSegments = roadSegmentRepository.findAllIntersectingAreaWithinRadius(
				gu,
				dong,
				centerLng,
				centerLat,
				radiusMeter,
				limit);
			return toRoadNetworkResponse(gu, dong, roadSegments, roadSegments.size());
		}
		return getRoadNetwork(gu, dong, limit);
	}

	private AdminRoadNetworkResponse toRoadNetworkResponse(
		String gu,
		String dong,
		List<RoadSegment> roadSegments,
		long segmentCount) {
		List<Long> edgeIds = roadSegments.stream()
			.map(RoadSegment::getEdgeId)
			.toList();
		Map<Long, List<SegmentFeatureType>> featureTypesByEdgeId = loadSegmentFeatures(edgeIds)
			.stream()
			.collect(Collectors.groupingBy(
				SegmentFeature::getEdgeId,
				Collectors.mapping(SegmentFeature::getFeatureType, Collectors.toList())));

		List<AdminGeoJsonFeatureResponse<AdminLineStringGeometryResponse, AdminRoadSegmentPropertiesResponse>> features = roadSegments
			.stream()
			.map(roadSegment -> toRoadSegmentFeature(roadSegment, featureTypesByEdgeId))
			.toList();
		List<Long> visibleNodeIds = roadSegments.stream()
			.flatMap(roadSegment -> List.of(roadSegment.getFromNodeId(), roadSegment.getToNodeId()).stream())
			.collect(Collectors.toCollection(TreeSet::new))
			.stream()
			.toList();
		List<AdminGeoJsonFeatureResponse<AdminPointGeometryResponse, AdminRoadNodePropertiesResponse>> nodeFeatures = loadRoadNodes(visibleNodeIds)
			.stream()
			.sorted(Comparator.comparing(RoadNode::getVertexId))
			.map(this::toRoadNodeFeature)
			.toList();

		return new AdminRoadNetworkResponse(
			new AdminRoadNetworkSummaryResponse(segmentCount, features.size(), nodeFeatures.size(),
				nodeFeatures.size()),
			toBbox(roadSegments.stream()
				.map(RoadSegment::getGeom)
				.map(LineString::getEnvelopeInternal)
				.toList()),
			AdminGeoJsonFeatureCollectionResponse.of(features),
			AdminGeoJsonFeatureCollectionResponse.of(nodeFeatures),
			toAreaBoundaryFeature(gu, dong));
	}

	private List<SegmentFeature> loadSegmentFeatures(List<Long> edgeIds) {
		if (edgeIds.isEmpty()) {
			return List.of();
		}
		List<SegmentFeature> features = new ArrayList<>();
		for (List<Long> batch : chunkedLongValues(edgeIds, SEGMENT_FEATURE_QUERY_BATCH_SIZE)) {
			features.addAll(segmentFeatureRepository.findByEdgeIdIn(batch));
		}
		return features;
	}

	private List<RoadNode> loadRoadNodes(List<Long> nodeIds) {
		if (nodeIds.isEmpty()) {
			return List.of();
		}
		List<RoadNode> nodes = new ArrayList<>();
		for (List<Long> batch : chunkedLongValues(nodeIds, ROAD_NODE_QUERY_BATCH_SIZE)) {
			roadNodeRepository.findAllById(batch).forEach(nodes::add);
		}
		return nodes;
	}

	public AdminGeoJsonFeatureResponse<AdminLineStringGeometryResponse, AdminRoadSegmentPropertiesResponse> getRoadSegment(
		Long edgeId,
		String gu,
		String dong) {
		if (!hasGu(gu)) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, "구는 필수입니다.");
		}
		boolean inScope = hasAreaScope(gu, dong)
			? roadSegmentRepository.existsIntersectingAreaByEdgeId(edgeId, gu, dong)
			: roadSegmentRepository.existsIntersectingGuByEdgeId(edgeId, gu);
		if (!inScope) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, "담당 구/동의 segment만 조회할 수 있습니다.");
		}
		RoadSegment roadSegment = roadSegmentRepository.findById(edgeId)
			.orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "segment를 찾을 수 없습니다."));
		List<SegmentFeatureType> featureTypes = segmentFeatureRepository.findByEdgeIdIn(List.of(edgeId))
			.stream()
			.map(SegmentFeature::getFeatureType)
			.toList();
		return toRoadSegmentFeature(roadSegment, Map.of(edgeId, featureTypes));
	}

	public AdminRoadNetworkBridgePayloadResponse getRoadNetworkBridges(String gu, String dong) {
		if (!hasGu(gu)) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, "구는 필수입니다.");
		}
		List<RoadSegment> roadSegments = hasAreaScope(gu, dong)
			? roadSegmentRepository.findAllIntersectingArea(gu, dong)
			: roadSegmentRepository.findAllIntersectingGu(gu);
		BridgeGraph bridgeGraph = buildBridgeGraph(roadSegments);
		List<BridgeCandidate> candidates = findBridgeCandidates(bridgeGraph);
		List<AdminGeoJsonFeatureResponse<AdminLineStringGeometryResponse, AdminRoadNetworkBridgePropertiesResponse>> features = candidates
			.stream()
			.map(this::toBridgeFeature)
			.toList();

		return new AdminRoadNetworkBridgePayloadResponse(
			new AdminRoadNetworkBridgeSummaryResponse(
				bridgeGraph.componentCount(),
				bridgeGraph.endpointCount(),
				candidates.size(),
				features.size(),
				BRIDGE_MAX_DISTANCE_METER,
				BRIDGE_AUTO_DISTANCE_METER),
			toBbox(features.stream()
				.map(feature -> toEnvelope(feature.geometry().coordinates()))
				.toList()),
			AdminGeoJsonFeatureCollectionResponse.of(features));
	}

	public AdminFacilityPayloadResponse getFacilities(String gu, String dong, int limit) {
		List<Place> places;
		if (hasAreaScope(gu, dong)) {
			places = placeRepository.findAllIntersectingArea(gu, dong, limit);
		} else if (hasGu(gu)) {
			places = placeRepository.findAllIntersectingGu(gu, limit);
		} else {
			places = placeRepository.findAll(PageRequest.of(0, limit, PLACE_SORT)).getContent();
		}
		List<AdminGeoJsonFeatureResponse<AdminPointGeometryResponse, AdminFacilityPropertiesResponse>> features = places
			.stream()
			.map(this::toFacilityFeature)
			.toList();
		Map<String, Long> categoryCounts = places.stream()
			.collect(Collectors.groupingBy(
				place -> place.getCategory().name(),
				LinkedHashMap::new,
				Collectors.counting()));

		return new AdminFacilityPayloadResponse(
			new AdminFacilitySummaryResponse(
				features.size(),
				features.size(),
				places.stream()
					.filter(place -> place.getProviderPlaceId() != null && !place.getProviderPlaceId().isBlank())
					.count(),
				sortMap(categoryCounts),
				sortMap(categoryCounts)),
			toBbox(places.stream()
				.map(Place::getPoint)
				.map(Point::getEnvelopeInternal)
				.toList()),
			AdminGeoJsonFeatureCollectionResponse.of(features),
			toAreaBoundaryFeature(gu, dong));
	}

	public AdminPlaceDetailResponse getPlace(Long placeId) {
		Place place = getPlaceWithAccessibilityFeatures(placeId);
		return AdminPlaceDetailResponse.of(place, geoPointConverter);
	}

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public AdminRoadSegmentUpdateResponse updateRoadSegmentAttributes(
		UUID userId,
		Long edgeId,
		String gu,
		String dong,
		AdminRoadSegmentAttributesUpdateRequest request) {
		RoadSegmentUpdateContext updateContext;
		try {
			updateContext = Objects.requireNonNull(
				transactionTemplate.execute(transactionStatus -> updateRoadSegmentAttributesInTransaction(
					userId,
					edgeId,
					gu,
					dong,
					request)));
		} catch (ObjectOptimisticLockingFailureException exception) {
			throw new BusinessException(CommonErrorCode.CONFLICT, "다른 관리자가 먼저 수정했습니다. 새로고침 후 다시 시도하세요.", exception);
		}
		return new AdminRoadSegmentUpdateResponse(
			updateContext.after(),
			updateContext.routingApplyPending() ? AdminRoutingApplyStatus.PENDING : AdminRoutingApplyStatus.SKIPPED,
			updateContext.routingApplyPending()
				? "DB 저장이 완료되었습니다. 경로 반영이 필요합니다."
				: "경로 반영 대상 변경이 없습니다.");
	}

	public boolean applyRouteReviewSegmentDraftsInCurrentTransaction(
		UUID userId,
		String gu,
		String dong,
		List<HazardReportRouteReviewSegmentDraft> drafts) {
		boolean routingOverlayReloadRequired = false;
		try {
			for (HazardReportRouteReviewSegmentDraft draft : drafts) {
				if (!roadSegmentRepository.existsIntersectingAreaByEdgeId(draft.getEdgeId(), gu, dong)) {
					throw new BusinessException(CommonErrorCode.INVALID_INPUT, "route review segment is outside editable area.");
				}
				RoadSegment roadSegment = roadSegmentRepository.findById(draft.getEdgeId())
					.orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "segment not found."));
				AdminRoadSegmentPropertiesResponse before = toRoadSegmentProperties(roadSegment);
				roadSegment.updateAttributes(
					draft.getWalkAccess(),
					draft.getBrailleBlockState(),
					draft.getAudioSignalState(),
					draft.getWidthState(),
					draft.getSurfaceState(),
					draft.getStairsState(),
					draft.getSignalState());
				AdminRoadSegmentPropertiesResponse after = toRoadSegmentProperties(roadSegment);
				adminAuditLogService.record(
					userId,
					"HAZARD_ROUTE_REVIEW_SEGMENT_APPLY",
					"ROAD_SEGMENT",
					String.valueOf(draft.getEdgeId()),
					gu,
					dong,
					"route review segment draft applied edgeId=" + draft.getEdgeId(),
					before,
					after);
				routingOverlayReloadRequired = patchRoutingOverride(
					draft.getEdgeId(),
					draft.getWalkAccess(),
					draft.getStairsState(),
					draft.getWidthState(),
					draft.getBrailleBlockState(),
					draft.getWalkAccess() != null,
					draft.getStairsState() != null,
					draft.getWidthState() != null,
					draft.getBrailleBlockState() != null) || routingOverlayReloadRequired;
			}
		} catch (ObjectOptimisticLockingFailureException exception) {
			throw new BusinessException(CommonErrorCode.CONFLICT, "다른 관리자가 먼저 수정했습니다. 새로고침 후 다시 시도하세요.", exception);
		}
		if (routingOverlayReloadRequired) {
			adminRoutingApplyService.markDirtyInCurrentTransaction();
		}
		return routingOverlayReloadRequired;
	}

	@Transactional
	public AdminPlaceDetailResponse updatePlace(
		UUID userId,
		Long placeId,
		String gu,
		String dong,
		AdminPlaceUpdateRequest request) {
		validateEditablePlace(userId, placeId, gu, dong);
		Place place = getPlaceWithAccessibilityFeatures(placeId);
		AdminPlaceDetailResponse before = AdminPlaceDetailResponse.of(place, geoPointConverter);
		validateProviderPlaceIdOwner(placeId, normalizeNullableText(request.providerPlaceId()));
		place.updateBasicInfo(
			request.name(),
			request.category(),
			request.address(),
			request.point() == null ? null : geoPointConverter.toPoint(request.point()),
			request.providerPlaceId());
		AdminPlaceDetailResponse after = AdminPlaceDetailResponse.of(place, geoPointConverter);
		adminAuditLogService.record(
			userId,
			"PLACE_BASIC_UPDATE",
			"PLACE",
			String.valueOf(placeId),
			gu,
			dong,
			"편의시설 기본 정보 변경 placeId=" + placeId,
			before,
			after);
		return after;
	}

	@Transactional
	public AdminPlaceDetailResponse updatePlaceAccessibilityFeatures(
		UUID userId,
		Long placeId,
		String gu,
		String dong,
		AdminPlaceAccessibilityFeaturesUpdateRequest request) {
		validateEditablePlace(userId, placeId, gu, dong);
		Place beforePlace = getPlaceWithAccessibilityFeatures(placeId);
		AdminPlaceDetailResponse before = AdminPlaceDetailResponse.of(beforePlace, geoPointConverter);
		validateUniqueFeatureTypes(request.features());
		if (hasSameAccessibilityFeatures(beforePlace, request.features())) {
			return before;
		}
		Place place = requirePlace(placeId);
		placeAccessibilityFeatureRepository.deleteAllByPlace_PlaceId(placeId);
		placeAccessibilityFeatureRepository.flush();
		List<PlaceAccessibilityFeature> savedFeatures = placeAccessibilityFeatureRepository.saveAll(
			request.features()
				.stream()
				.map(feature -> PlaceAccessibilityFeature.create(
					place,
					feature.featureType(),
					feature.isAvailable()))
				.toList());
		AdminPlaceDetailResponse after = AdminPlaceDetailResponse.of(place, savedFeatures, geoPointConverter);
		adminAuditLogService.record(
			userId,
			"PLACE_ACCESSIBILITY_FEATURES_REPLACE",
			"PLACE",
			String.valueOf(placeId),
			gu,
			dong,
			"편의시설 접근성 속성 교체 placeId=" + placeId,
			before,
			after);
		return after;
	}

	private BridgeGraph buildBridgeGraph(List<RoadSegment> roadSegments) {
		BridgeUnionFind unionFind = new BridgeUnionFind();
		Map<Long, Integer> degreesByNodeId = new HashMap<>();
		Map<Long, BridgeNode> nodesById = new HashMap<>();
		List<GraphSegment> graphSegments = new ArrayList<>();

		for (RoadSegment roadSegment : roadSegments) {
			Long fromNodeId = roadSegment.getFromNodeId();
			Long toNodeId = roadSegment.getToNodeId();
			List<BridgeCoord> coordinates = toBridgeCoords(roadSegment.getGeom());
			if (coordinates.size() < 2) {
				continue;
			}
			BridgeCoord start = coordinates.get(0);
			BridgeCoord end = coordinates.get(coordinates.size() - 1);
			nodesById.putIfAbsent(fromNodeId, new BridgeNode(fromNodeId, start));
			nodesById.putIfAbsent(toNodeId, new BridgeNode(toNodeId, end));
			degreesByNodeId.merge(fromNodeId, 1, Integer::sum);
			degreesByNodeId.merge(toNodeId, 1, Integer::sum);
			unionFind.add(fromNodeId);
			unionFind.add(toNodeId);
			unionFind.union(fromNodeId, toNodeId);
			graphSegments.add(new GraphSegment(
				roadSegment.getEdgeId(),
				fromNodeId,
				toNodeId,
				roadSegment.getSegmentType(),
				coordinates,
				roadSegment.getGeom().getEnvelopeInternal(),
				null));
		}

		Map<Long, Integer> componentIndexes = new HashMap<>();
		List<GraphSegment> segmentsWithComponent = graphSegments.stream()
			.map(segment -> {
				Long root = unionFind.find(segment.fromNodeId());
				Integer componentIndex = componentIndexes.computeIfAbsent(root, ignored -> componentIndexes.size() + 1);
				return segment.withComponentId(componentIndex);
			})
			.toList();
		Map<Long, Integer> componentByNodeId = new HashMap<>();
		nodesById.keySet()
			.forEach(nodeId -> {
				Long root = unionFind.find(nodeId);
				componentByNodeId.put(nodeId,
					componentIndexes.computeIfAbsent(root, ignored -> componentIndexes.size() + 1));
			});
		List<BridgeEndpoint> endpoints = nodesById.values()
			.stream()
			.filter(node -> degreesByNodeId.getOrDefault(node.nodeId(), 0) <= 1)
			.map(node -> new BridgeEndpoint(
				node.nodeId(),
				node.coord(),
				componentByNodeId.getOrDefault(node.nodeId(), 0)))
			.toList();
		return new BridgeGraph(segmentsWithComponent, endpoints, componentIndexes.size());
	}

	private List<BridgeCandidate> findBridgeCandidates(BridgeGraph bridgeGraph) {
		STRtree segmentIndex = new STRtree();
		bridgeGraph.segments().forEach(segment -> segmentIndex.insert(segment.envelope(), segment));
		List<BridgeCandidate> candidates = new ArrayList<>();
		Set<String> candidateKeys = new HashSet<>();

		for (BridgeEndpoint endpoint : bridgeGraph.endpoints()) {
			ClosestBridgeTarget nearest = findNearestBridgeTarget(endpoint, segmentIndex);
			if (nearest == null) {
				continue;
			}
			String key = endpoint.nodeId() + ":" + nearest.segment().edgeId();
			if (!candidateKeys.add(key)) {
				continue;
			}
			String priority = nearest.distanceMeter() <= BRIDGE_AUTO_DISTANCE_METER ? "AUTO" : "REVIEW";
			candidates.add(new BridgeCandidate(
				"bridge-" + (candidates.size() + 1),
				priority,
				endpoint.nodeId(),
				endpoint.componentId(),
				nearest.segment().edgeId(),
				nearest.segment().componentId(),
				roundMeter(nearest.distanceMeter()),
				endpoint.coord(),
				nearest.coord()));
		}

		return candidates.stream()
			.sorted(Comparator
				.comparing(BridgeCandidate::distanceMeter)
				.thenComparing(BridgeCandidate::fromNodeId)
				.thenComparing(BridgeCandidate::toEdgeId))
			.toList();
	}

	@SuppressWarnings("unchecked")
	private ClosestBridgeTarget findNearestBridgeTarget(BridgeEndpoint endpoint, STRtree segmentIndex) {
		Envelope queryEnvelope = new Envelope(
			endpoint.coord().lng(),
			endpoint.coord().lng(),
			endpoint.coord().lat(),
			endpoint.coord().lat());
		queryEnvelope.expandBy(toDegreeExpand(endpoint.coord().lat(), BRIDGE_MAX_DISTANCE_METER));
		List<GraphSegment> nearbySegments = segmentIndex.query(queryEnvelope);

		ClosestBridgeTarget nearest = null;
		for (GraphSegment segment : nearbySegments) {
			if (!isBridgeTargetSegment(segment) || segment.componentId() == endpoint.componentId()) {
				continue;
			}
			ClosestBridgePoint closestPoint = closestPointOnLine(endpoint.coord(), segment.coordinates());
			if (closestPoint.distanceMeter() > BRIDGE_MAX_DISTANCE_METER) {
				continue;
			}
			if (nearest == null || closestPoint.distanceMeter() < nearest.distanceMeter()
				|| closestPoint.distanceMeter() == nearest.distanceMeter()
					&& segment.edgeId() < nearest.segment().edgeId()) {
				nearest = new ClosestBridgeTarget(segment, closestPoint.coord(), closestPoint.distanceMeter());
			}
		}
		return nearest;
	}

	private boolean isBridgeTargetSegment(GraphSegment segment) {
		return segment.segmentType() == SegmentType.SIDE_LINE;
	}

	private AdminGeoJsonFeatureResponse<AdminLineStringGeometryResponse, AdminRoadNetworkBridgePropertiesResponse> toBridgeFeature(
		BridgeCandidate candidate) {
		List<List<Double>> coordinates = List.of(
			List.of(candidate.fromCoord().lng(), candidate.fromCoord().lat()),
			List.of(candidate.toCoord().lng(), candidate.toCoord().lat()));
		return AdminGeoJsonFeatureResponse.of(
			AdminLineStringGeometryResponse.of(coordinates),
			new AdminRoadNetworkBridgePropertiesResponse(
				candidate.candidateId(),
				"PROPOSED_BRIDGE",
				candidate.priority(),
				String.valueOf(candidate.fromNodeId()),
				String.valueOf(candidate.fromComponentId()),
				String.valueOf(candidate.toEdgeId()),
				String.valueOf(candidate.toComponentId()),
				candidate.distanceMeter(),
				List.of(candidate.fromCoord().lng(), candidate.fromCoord().lat()),
				"다른 컴포넌트 endpoint와 가까운 보행 segment 연결 후보"));
	}

	private List<BridgeCoord> toBridgeCoords(LineString lineString) {
		return Arrays.stream(lineString.getCoordinates())
			.map(coordinate -> new BridgeCoord(coordinate.getX(), coordinate.getY()))
			.toList();
	}

	private ClosestBridgePoint closestPointOnLine(BridgeCoord point, List<BridgeCoord> coordinates) {
		ClosestBridgePoint nearest = null;
		for (int i = 1; i < coordinates.size(); i++) {
			ClosestBridgePoint projected = closestPointOnLineSegment(point, coordinates.get(i - 1), coordinates.get(i));
			if (nearest == null || projected.distanceMeter() < nearest.distanceMeter()) {
				nearest = projected;
			}
		}
		return nearest;
	}

	private ClosestBridgePoint closestPointOnLineSegment(BridgeCoord point, BridgeCoord start, BridgeCoord end) {
		double originLat = point.lat();
		LocalMeterCoord pointMeter = toLocalMeters(point, originLat);
		LocalMeterCoord startMeter = toLocalMeters(start, originLat);
		LocalMeterCoord endMeter = toLocalMeters(end, originLat);
		double dx = endMeter.x() - startMeter.x();
		double dy = endMeter.y() - startMeter.y();
		double lengthSquared = dx * dx + dy * dy;
		double t = lengthSquared == 0
			? 0
			: Math.max(0, Math.min(1,
				((pointMeter.x() - startMeter.x()) * dx + (pointMeter.y() - startMeter.y()) * dy) / lengthSquared));
		LocalMeterCoord projectedMeter = new LocalMeterCoord(startMeter.x() + dx * t, startMeter.y() + dy * t);
		double distanceMeter = Math.hypot(pointMeter.x() - projectedMeter.x(), pointMeter.y() - projectedMeter.y());
		return new ClosestBridgePoint(toLngLat(projectedMeter, originLat), distanceMeter);
	}

	private LocalMeterCoord toLocalMeters(BridgeCoord coord, double originLat) {
		double metersPerDegreeLat = 111_320.0;
		double metersPerDegreeLng = 111_320.0 * Math.cos(Math.toRadians(originLat));
		return new LocalMeterCoord(coord.lng() * metersPerDegreeLng, coord.lat() * metersPerDegreeLat);
	}

	private BridgeCoord toLngLat(LocalMeterCoord coord, double originLat) {
		double metersPerDegreeLat = 111_320.0;
		double metersPerDegreeLng = 111_320.0 * Math.cos(Math.toRadians(originLat));
		return new BridgeCoord(coord.x() / metersPerDegreeLng, coord.y() / metersPerDegreeLat);
	}

	private double toDegreeExpand(double lat, double meter) {
		double lngDegree = meter / (111_320.0 * Math.cos(Math.toRadians(lat)));
		double latDegree = meter / 111_320.0;
		return Math.max(lngDegree, latDegree);
	}

	private double roundMeter(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	private Envelope toEnvelope(List<List<Double>> coordinates) {
		Envelope envelope = new Envelope();
		coordinates.forEach(coord -> envelope.expandToInclude(coord.get(0), coord.get(1)));
		return envelope;
	}

	private boolean hasGu(String gu) {
		return gu != null && !gu.isBlank();
	}

	private boolean hasAreaScope(String gu, String dong) {
		return hasGu(gu) && dong != null && !dong.isBlank() && !ALL_DONG.equals(dong);
	}

	private boolean hasClip(Double centerLat, Double centerLng, Integer radiusMeter) {
		if (centerLat == null && centerLng == null && radiusMeter == null) {
			return false;
		}
		if (centerLat == null || centerLng == null || radiusMeter == null) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, "중심 좌표와 반경은 함께 지정해야 합니다.");
		}
		if (radiusMeter <= 0) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, "반경은 1m 이상이어야 합니다.");
		}
		return true;
	}

	private List<List<Long>> chunkedLongValues(List<Long> values, int chunkSize) {
		List<List<Long>> batches = new ArrayList<>();
		for (int start = 0; start < values.size(); start += chunkSize) {
			batches.add(values.subList(start, Math.min(start + chunkSize, values.size())));
		}
		return batches;
	}

	private Place requirePlace(Long placeId) {
		return placeRepository.findById(placeId)
			.orElseThrow(() -> new PlaceException(PlaceErrorCode.PLACE_NOT_FOUND));
	}

	private Place getPlaceWithAccessibilityFeatures(Long placeId) {
		return placeRepository.findWithAccessibilityFeaturesByPlaceId(placeId)
			.orElseThrow(() -> new PlaceException(PlaceErrorCode.PLACE_NOT_FOUND));
	}

	private void validateProviderPlaceIdOwner(Long placeId, String providerPlaceId) {
		if (providerPlaceId == null) {
			return;
		}
		placeRepository.findByProviderPlaceId(providerPlaceId)
			.filter(place -> !place.getPlaceId().equals(placeId))
			.ifPresent(ignored -> {
				throw new PlaceException(PlaceErrorCode.INVALID_PLACE_REQUEST, "이미 다른 장소에 연결된 providerPlaceId입니다.");
			});
	}

	private void validateEditablePlace(UUID userId, Long placeId, String gu, String dong) {
		adminService.requireCanEditArea(userId, gu, dong, AdminAreaAssignmentType.FACILITY);
		if (!placeRepository.existsIntersectingGuByPlaceId(placeId, gu)) {
			throw new PlaceException(PlaceErrorCode.INVALID_PLACE_REQUEST, "담당 구의 장소만 수정할 수 있습니다.");
		}
	}

	private AdminGeoJsonFeatureResponse<JsonNode, AdminAreaBoundaryPropertiesResponse> toAreaBoundaryFeature(String gu,
		String dong) {
		if (!hasGu(gu)) {
			return null;
		}
		String geoJson = hasAreaScope(gu, dong)
			? adminAreaRepository.findAreaBoundaryGeoJson(gu, dong)
			: adminAreaRepository.findGuBoundaryGeoJson(gu);
		if (geoJson == null || geoJson.isBlank()) {
			return null;
		}
		try {
			return AdminGeoJsonFeatureResponse.of(
				objectMapper.readTree(geoJson),
				new AdminAreaBoundaryPropertiesResponse(gu, hasAreaScope(gu, dong) ? dong : ALL_DONG));
		} catch (JsonProcessingException ignored) {
			return null;
		}
	}

	private void validateUniqueFeatureTypes(
		List<AdminPlaceAccessibilityFeaturesUpdateRequest.Feature> features) {
		Set<AccessibilityFeatureType> featureTypes = EnumSet.noneOf(AccessibilityFeatureType.class);
		for (AdminPlaceAccessibilityFeaturesUpdateRequest.Feature feature : features) {
			if (!featureTypes.add(feature.featureType())) {
				throw new PlaceException(PlaceErrorCode.INVALID_PLACE_REQUEST, "접근성 속성 유형은 중복될 수 없습니다.");
			}
		}
	}

	private boolean hasSameAccessibilityFeatures(
		Place place,
		List<AdminPlaceAccessibilityFeaturesUpdateRequest.Feature> requestedFeatures) {
		Map<AccessibilityFeatureType, Boolean> currentFeatures = new EnumMap<>(AccessibilityFeatureType.class);
		place.getAccessibilityFeatures()
			.forEach(feature -> currentFeatures.put(feature.getFeatureType(), feature.isAvailable()));
		Map<AccessibilityFeatureType, Boolean> nextFeatures = new EnumMap<>(AccessibilityFeatureType.class);
		requestedFeatures
			.forEach(feature -> nextFeatures.put(feature.featureType(), Boolean.TRUE.equals(feature.isAvailable())));
		return currentFeatures.equals(nextFeatures);
	}

	private String normalizeNullableText(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private String toSyntheticDong(String dong) {
		return dong.replace("1\uB3D9", "\uB3D9")
			.replace("2\uB3D9", "\uB3D9")
			.replace("3\uB3D9", "\uB3D9")
			.replace("4\uB3D9", "\uB3D9");
	}

	private RoadSegmentUpdateContext updateRoadSegmentAttributesInTransaction(
		UUID userId,
		Long edgeId,
		String gu,
		String dong,
		AdminRoadSegmentAttributesUpdateRequest request) {
		adminService.requireCanEditArea(userId, gu, dong, AdminAreaAssignmentType.ROAD_NETWORK);
		if (!roadSegmentRepository.existsIntersectingGuByEdgeId(edgeId, gu)) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, "담당 구/동의 segment만 수정할 수 있습니다.");
		}
		RoadSegment roadSegment = roadSegmentRepository.findById(edgeId)
			.orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "segment를 찾을 수 없습니다."));
		AdminRoadSegmentPropertiesResponse before = toRoadSegmentProperties(roadSegment);
		roadSegment.updateAttributes(
			request.walkAccess(),
			request.brailleBlockState(),
			request.audioSignalState(),
			request.widthState(),
			request.surfaceState(),
			request.stairsState(),
			request.signalState());
		AdminRoadSegmentPropertiesResponse after = toRoadSegmentProperties(roadSegment);
		adminAuditLogService.record(
			userId,
			"ROAD_SEGMENT_ATTRIBUTES_UPDATE",
			"ROAD_SEGMENT",
			String.valueOf(edgeId),
			gu,
			dong,
			"보행 segment 속성 변경 edgeId=" + edgeId,
			before,
			after);
		boolean routingOverlayReloadRequired = applyRoutingOverride(edgeId, request);
		if (routingOverlayReloadRequired) {
			adminRoutingApplyService.markDirtyInCurrentTransaction();
		}
		return new RoadSegmentUpdateContext(
			before,
			after,
			routingOverlayReloadRequired);
	}

	private boolean applyRoutingOverride(Long edgeId, AdminRoadSegmentAttributesUpdateRequest request) {
		if (!request.hasRoutingOverlayTargetField()) {
			return false;
		}
		return patchRoutingOverride(
			edgeId,
			request.walkAccess(),
			request.stairsState(),
			request.widthState(),
			request.brailleBlockState(),
			request.hasWalkAccessField(),
			request.hasStairsStateField(),
			request.hasWidthStateField(),
			request.hasBrailleBlockStateField());
	}

	private boolean patchRoutingOverride(
		Long edgeId,
		AccessibilityState walkAccess,
		AccessibilityState stairsState,
		WidthState widthState,
		AccessibilityState brailleBlockState,
		boolean patchWalkAccess,
		boolean patchStairsState,
		boolean patchWidthState,
		boolean patchBrailleBlockState) {
		if (!patchWalkAccess && !patchStairsState && !patchWidthState && !patchBrailleBlockState) {
			return false;
		}
		RoutingSegmentOverride currentOverride = routingSegmentOverrideRepository.findById(edgeId)
			.orElseGet(() -> RoutingSegmentOverride.of(edgeId, null, null, null, null));
		AccessibilityState nextWalkAccess = patchWalkAccess ? walkAccess : currentOverride.getWalkAccess();
		AccessibilityState nextStairsState = patchStairsState ? stairsState : currentOverride.getStairsState();
		WidthState nextWidthState = patchWidthState ? widthState : currentOverride.getWidthState();
		AccessibilityState nextBrailleBlockState = patchBrailleBlockState
			? brailleBlockState
			: currentOverride.getBrailleBlockState();
		currentOverride.update(nextWalkAccess, nextStairsState, nextWidthState, nextBrailleBlockState);
		if (!currentOverride.hasAnyOverride()) {
			routingSegmentOverrideRepository.deleteById(edgeId);
		} else {
			routingSegmentOverrideRepository.save(currentOverride);
		}
		return true;
	}

	private AdminGeoJsonFeatureResponse<AdminLineStringGeometryResponse, AdminRoadSegmentPropertiesResponse> toRoadSegmentFeature(
		RoadSegment roadSegment,
		Map<Long, List<SegmentFeatureType>> featureTypesByEdgeId) {
		return AdminGeoJsonFeatureResponse.of(
			AdminLineStringGeometryResponse.of(toCoordinates(roadSegment.getGeom())),
			toRoadSegmentProperties(roadSegment,
				featureTypesByEdgeId.getOrDefault(roadSegment.getEdgeId(), List.of())));
	}

	private AdminRoadSegmentPropertiesResponse toRoadSegmentProperties(RoadSegment roadSegment) {
		return toRoadSegmentProperties(roadSegment, List.of());
	}

	private AdminRoadSegmentPropertiesResponse toRoadSegmentProperties(
		RoadSegment roadSegment,
		List<SegmentFeatureType> featureTypes) {
		return new AdminRoadSegmentPropertiesResponse(
			roadSegment.getEdgeId(),
			roadSegment.getFromNodeId(),
			roadSegment.getToNodeId(),
			roadSegment.getSegmentType(),
			roadSegment.getLengthMeter(),
			roadSegment.getAvgSlopePercent(),
			roadSegment.getWidthMeter(),
			roadSegment.getWalkAccess(),
			roadSegment.getBrailleBlockState(),
			roadSegment.getAudioSignalState(),
			roadSegment.getWidthState(),
			roadSegment.getSurfaceState(),
			roadSegment.getStairsState(),
			roadSegment.getSignalState(),
			featureTypes.stream()
				.distinct()
				.sorted(Comparator.comparing(Enum::name))
				.toList());
	}

	private AdminGeoJsonFeatureResponse<AdminPointGeometryResponse, AdminRoadNodePropertiesResponse> toRoadNodeFeature(
		RoadNode roadNode) {
		Point point = roadNode.getPoint();
		return AdminGeoJsonFeatureResponse.of(
			AdminPointGeometryResponse.of(point.getX(), point.getY()),
			new AdminRoadNodePropertiesResponse(
				roadNode.getVertexId(),
				roadNode.getSourceNodeKey()));
	}

	private AdminGeoJsonFeatureResponse<AdminPointGeometryResponse, AdminFacilityPropertiesResponse> toFacilityFeature(
		Place place) {
		Point point = place.getPoint();
		return AdminGeoJsonFeatureResponse.of(
			AdminPointGeometryResponse.of(point.getX(), point.getY()),
			new AdminFacilityPropertiesResponse(
				String.valueOf(place.getPlaceId()),
				place.getName(),
				place.getCategory(),
				place.getAddress() == null ? "" : place.getAddress(),
				place.getProviderPlaceId()));
	}

	private List<List<Double>> toCoordinates(LineString lineString) {
		return Arrays.stream(lineString.getCoordinates())
			.map(coordinate -> List.of(coordinate.getX(), coordinate.getY()))
			.toList();
	}

	private List<Double> toBbox(List<Envelope> envelopes) {
		if (envelopes.isEmpty()) {
			return null;
		}
		Envelope bbox = new Envelope();
		envelopes.forEach(bbox::expandToInclude);
		return List.of(bbox.getMinX(), bbox.getMinY(), bbox.getMaxX(), bbox.getMaxY());
	}

	private Map<String, Long> sortMap(Map<String, Long> values) {
		return values.entrySet()
			.stream()
			.sorted(Comparator.comparing(Map.Entry::getKey))
			.collect(Collectors.toMap(
				Map.Entry::getKey,
				Map.Entry::getValue,
				(left, right) -> left,
				LinkedHashMap::new));
	}

	private record BridgeGraph(
		List<GraphSegment> segments,
		List<BridgeEndpoint> endpoints,
		int componentCount) {

		int endpointCount() {
			return endpoints.size();
		}
	}

	private record GraphSegment(
		Long edgeId,
		Long fromNodeId,
		Long toNodeId,
		SegmentType segmentType,
		List<BridgeCoord> coordinates,
		Envelope envelope,
		Integer componentId) {

		GraphSegment withComponentId(Integer nextComponentId) {
			return new GraphSegment(edgeId, fromNodeId, toNodeId, segmentType, coordinates, envelope, nextComponentId);
		}
	}

	private record BridgeNode(
		Long nodeId,
		BridgeCoord coord) {
	}

	private record BridgeEndpoint(
		Long nodeId,
		BridgeCoord coord,
		Integer componentId) {
	}

	private record BridgeCandidate(
		String candidateId,
		String priority,
		Long fromNodeId,
		Integer fromComponentId,
		Long toEdgeId,
		Integer toComponentId,
		Double distanceMeter,
		BridgeCoord fromCoord,
		BridgeCoord toCoord) {
	}

	private record ClosestBridgeTarget(
		GraphSegment segment,
		BridgeCoord coord,
		Double distanceMeter) {
	}

	private record ClosestBridgePoint(
		BridgeCoord coord,
		Double distanceMeter) {
	}

	private record BridgeCoord(
		double lng,
		double lat) {
	}

	private record LocalMeterCoord(
		double x,
		double y) {
	}

	private static final class BridgeUnionFind {

		private final Map<Long, Long> parentByNodeId = new HashMap<>();

		private void add(Long nodeId) {
			parentByNodeId.putIfAbsent(nodeId, nodeId);
		}

		private Long find(Long nodeId) {
			Long parent = parentByNodeId.get(nodeId);
			if (parent == null || parent.equals(nodeId)) {
				return nodeId;
			}
			Long root = find(parent);
			parentByNodeId.put(nodeId, root);
			return root;
		}

		private void union(Long left, Long right) {
			Long leftRoot = find(left);
			Long rightRoot = find(right);
			if (!leftRoot.equals(rightRoot)) {
				parentByNodeId.put(rightRoot, leftRoot);
			}
		}
	}
	private record RoadSegmentUpdateContext(
		AdminRoadSegmentPropertiesResponse before,
		AdminRoadSegmentPropertiesResponse after,
		boolean routingApplyPending) {
	}
}
