package com.ssafy.e102.domain.route.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.ssafy.e102.domain.route.dto.request.WalkRouteSearchRequest;
import com.ssafy.e102.domain.route.dto.response.LowFloorBusReservationResponse;
import com.ssafy.e102.domain.route.dto.response.RouteGuidanceEventResponse;
import com.ssafy.e102.domain.route.dto.response.RouteGuidanceEventType;
import com.ssafy.e102.domain.route.dto.response.RouteLegResponse;
import com.ssafy.e102.domain.route.dto.response.RouteStopResponse;
import com.ssafy.e102.domain.route.dto.response.RouteSummaryResponse;
import com.ssafy.e102.domain.route.dto.response.TransitLaneOptionResponse;
import com.ssafy.e102.domain.route.dto.response.WalkRouteSearchResponse;
import com.ssafy.e102.domain.route.entity.SubwayStation;
import com.ssafy.e102.domain.route.entity.SubwayStationElevator;
import com.ssafy.e102.domain.route.entity.SubwayTimetable;
import com.ssafy.e102.domain.route.exception.RouteErrorCode;
import com.ssafy.e102.domain.route.exception.RouteException;
import com.ssafy.e102.domain.route.repository.SubwayStationElevatorRepository;
import com.ssafy.e102.domain.route.repository.SubwayStationRepository;
import com.ssafy.e102.domain.route.repository.SubwayTimetableRepository;
import com.ssafy.e102.domain.route.type.RouteBadge;
import com.ssafy.e102.domain.route.type.RouteLegRole;
import com.ssafy.e102.domain.route.type.RouteOption;
import com.ssafy.e102.domain.route.type.RouteWarningCode;
import com.ssafy.e102.domain.route.type.SubwayServiceDayType;
import com.ssafy.e102.domain.route.type.TransportMode;
import com.ssafy.e102.domain.route.type.WalkRouteProfile;
import com.ssafy.e102.domain.user.type.MobilitySubtype;
import com.ssafy.e102.domain.user.type.PrimaryUserType;
import com.ssafy.e102.global.external.bims.BusanBimsArrival;
import com.ssafy.e102.global.external.bims.BusanBimsClient;
import com.ssafy.e102.global.external.bims.BusanBimsClientConfig;
import com.ssafy.e102.global.external.graphhopper.GraphHopperRouteClient;
import com.ssafy.e102.global.external.graphhopper.GraphHopperRoutePath;
import com.ssafy.e102.global.external.graphhopper.GraphHopperRouteRequest;
import com.ssafy.e102.global.external.odsay.OdsayClient;
import com.ssafy.e102.global.external.odsay.OdsayLaneGeometry;
import com.ssafy.e102.global.external.odsay.OdsayPassStop;
import com.ssafy.e102.global.external.odsay.OdsayTransitLane;
import com.ssafy.e102.global.external.odsay.OdsayTransitLeg;
import com.ssafy.e102.global.external.odsay.OdsayTransitPath;
import com.ssafy.e102.global.external.odsay.OdsayTransitSearchResult;
import com.ssafy.e102.global.geo.GeoDistanceCalculator;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

@Service
public class TransitRouteSearchService {

	private static final Logger log = LoggerFactory.getLogger(TransitRouteSearchService.class);

	private static final double BUSAN_MIN_LAT = 34.85;
	private static final double BUSAN_MAX_LAT = 35.45;
	private static final double BUSAN_MIN_LNG = 128.70;
	private static final double BUSAN_MAX_LNG = 129.40;
	private static final double START_END_MIN_DISTANCE_METER = 20.0;
	private static final int BUS_TRANSFER_BUFFER_SECOND = 3 * 60;
	private static final int SUBWAY_TRANSFER_BUFFER_SECOND = 5 * 60;
	private static final int WHEELCHAIR_SUBWAY_TRANSFER_BUFFER_SECOND = 8 * 60;
	private static final int DEFAULT_BUS_BOARDING_PREP_SECOND = 2 * 60;
	private static final int ACCESSIBLE_BUS_BOARDING_PREP_SECOND = 3 * 60;
	private static final int DAY_SECONDS = 24 * 60 * 60;
	private static final int OPTION_PRESELECT_LIMIT = 3;
	private static final int ODSAY_SHORTLIST_LIMIT = 5;
	private static final Duration BIMS_ENRICHMENT_TIMEOUT = Duration.ofSeconds(6);
	private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");
	private static final List<RouteBadge> BADGE_PRIORITY = List.of(
		RouteBadge.STAIR,
		RouteBadge.NARROW_SIDEWALK,
		RouteBadge.UNPAVED,
		RouteBadge.MIDDLE_SLOPE,
		RouteBadge.LOW_SLOPE,
		RouteBadge.CROSSWALK,
		RouteBadge.ELEVATOR);

	private final WalkRouteUserProfileQueryService userProfileQueryService;
	private final SubwayStationElevatorRepository subwayStationElevatorRepository;
	private final SubwayStationRepository subwayStationRepository;
	private final SubwayTimetableRepository subwayTimetableRepository;
	private final WalkRouteProfileService walkRouteProfileService;
	private final WalkRoutePayloadService walkRoutePayloadService;
	private final GraphHopperRouteClient graphHopperRouteClient;
	private final BusanBimsClient busanBimsClient;
	private final Executor bimsTaskExecutor;
	private final OdsayClient odsayClient;
	private final OdsayLoadLaneStore odsayLoadLaneStore;
	private final RouteSearchCacheService routeSearchCacheService;

	public TransitRouteSearchService(
		WalkRouteUserProfileQueryService userProfileQueryService,
		SubwayStationElevatorRepository subwayStationElevatorRepository,
		SubwayStationRepository subwayStationRepository,
		SubwayTimetableRepository subwayTimetableRepository,
		WalkRouteProfileService walkRouteProfileService,
		WalkRoutePayloadService walkRoutePayloadService,
		GraphHopperRouteClient graphHopperRouteClient,
		BusanBimsClient busanBimsClient,
		@Qualifier(BusanBimsClientConfig.BIMS_TASK_EXECUTOR)
		Executor bimsTaskExecutor,
		OdsayClient odsayClient,
		OdsayLoadLaneStore odsayLoadLaneStore,
		RouteSearchCacheService routeSearchCacheService) {
		this.userProfileQueryService = userProfileQueryService;
		this.subwayStationElevatorRepository = subwayStationElevatorRepository;
		this.subwayStationRepository = subwayStationRepository;
		this.subwayTimetableRepository = subwayTimetableRepository;
		this.walkRouteProfileService = walkRouteProfileService;
		this.walkRoutePayloadService = walkRoutePayloadService;
		this.graphHopperRouteClient = graphHopperRouteClient;
		this.busanBimsClient = busanBimsClient;
		this.bimsTaskExecutor = bimsTaskExecutor;
		this.odsayClient = odsayClient;
		this.odsayLoadLaneStore = odsayLoadLaneStore;
		this.routeSearchCacheService = routeSearchCacheService;
	}

	public WalkRouteSearchResponse search(UUID userId, WalkRouteSearchRequest request) {
		WalkRouteUserProfile profile = userProfileQueryService.getProfile(userId);
		GeoPointRequest startPoint = request.startPoint();
		GeoPointRequest endPoint = request.endPoint();
		validateServiceArea(startPoint, endPoint);
		validateStartEndDistance(startPoint, endPoint);

		OdsayTransitSearchResult searchResult = odsayClient.searchPubTransPath(startPoint, endPoint);
		String searchId = "rs_transit_" + UUID.randomUUID();
		List<OdsayPathCandidate> odsayShortlist = selectOdsayShortlist(searchResult.paths());
		Map<String, List<OdsayLaneGeometry>> laneGeometryByMapObj = new LinkedHashMap<>(
			odsayLoadLaneStore.findValidByMapObjIn(mapObjs(odsayShortlist)));
		List<TransitRouteBaseCandidate> baseCandidates = new ArrayList<>();
		RouteException firstExternalFailure = null;
		for (OdsayPathCandidate pathCandidate : odsayShortlist) {
			OdsayTransitPath path = pathCandidate.path();
			int routeIndex = pathCandidate.routeIndex();
			try {
				List<OdsayLaneGeometry> laneGeometries = laneGeometries(path.mapObj(), laneGeometryByMapObj);
				toCandidate(searchId, routeIndex, startPoint, endPoint, path, laneGeometries, profile, false)
					.map(candidate -> new TransitRouteBaseCandidate(routeIndex, path, candidate))
					.ifPresent(baseCandidates::add);
			} catch (RouteException exception) {
				if (exception.getErrorCode() == RouteErrorCode.EXTERNAL_ROUTE_API_TIMEOUT) {
					throw exception;
				}
				if (exception.getErrorCode() == RouteErrorCode.EXTERNAL_ROUTE_API_FAILED
					&& firstExternalFailure == null) {
					firstExternalFailure = exception;
				}
				log.warn(
					"transit candidate skipped provider={} operation={} routeIndex={} legIndex={} mapObj={} status={} message={}",
					"route",
					"candidate",
					routeIndex,
					"-",
					path.mapObj(),
					exception.getErrorCode().getStatus(),
					exception.getMessage(),
					exception);
			}
		}
		if (baseCandidates.isEmpty()) {
			if (firstExternalFailure != null) {
				throw firstExternalFailure;
			}
			throw new RouteException(RouteErrorCode.ROUTE_NOT_FOUND);
		}
		List<TransitRouteBaseCandidate> staticSelectedCandidates = selectStaticFinalCandidates(baseCandidates);
		List<TransitRouteCandidate> candidates = enrichBimsCandidates(staticSelectedCandidates, profile);
		if (candidates.isEmpty()) {
			throw new RouteException(RouteErrorCode.ROUTE_NOT_FOUND);
		}
		List<TransitRouteCandidate> selectedCandidates = selectCandidates(candidates);
		WalkRouteSearchResponse response = new WalkRouteSearchResponse(searchId,
			selectedCandidates.stream().map(TransitRouteCandidate::route).toList());
		routeSearchCacheService.save(userId, response);
		routeSearchCacheService.saveTransitMetadata(searchId,
			selectedCandidates.stream().map(TransitRouteCandidate::snapshot).toList());
		return response;
	}

	private List<String> mapObjs(List<OdsayPathCandidate> odsayShortlist) {
		return odsayShortlist.stream()
			.map(candidate -> candidate.path().mapObj())
			.filter(mapObj -> mapObj != null && !mapObj.isBlank())
			.distinct()
			.toList();
	}

	private List<OdsayLaneGeometry> laneGeometries(
		String mapObj,
		Map<String, List<OdsayLaneGeometry>> laneGeometryByMapObj) {
		List<OdsayLaneGeometry> cachedLaneGeometries = laneGeometryByMapObj.get(mapObj);
		if (cachedLaneGeometries != null) {
			return cachedLaneGeometries;
		}
		List<OdsayLaneGeometry> laneGeometries = odsayClient.loadLane(mapObj);
		if (!laneGeometries.isEmpty() && mapObj != null && !mapObj.isBlank()) {
			laneGeometryByMapObj.put(mapObj, laneGeometries);
			odsayLoadLaneStore.saveIfAbsentOrRepairMalformed(mapObj, laneGeometries);
		}
		return laneGeometries;
	}

	private List<OdsayPathCandidate> selectOdsayShortlist(List<OdsayTransitPath> paths) {
		List<OdsayPathCandidate> indexedPaths = new ArrayList<>();
		for (int index = 0; index < paths.size(); index++) {
			indexedPaths.add(new OdsayPathCandidate(index + 1, paths.get(index)));
		}
		Map<String, OdsayPathCandidate> selectedByRoute = new LinkedHashMap<>();
		addOdsayShortlistCandidates(selectedByRoute, indexedPaths, this::odsayRecommendedComparator);
		addOdsayShortlistCandidates(selectedByRoute, indexedPaths, this::odsayMinTransferComparator);
		addOdsayShortlistCandidates(selectedByRoute, indexedPaths, this::odsayMinWalkComparator);
		Comparator<OdsayPathCandidate> recommendedComparator = odsayRecommendedComparator();
		List<OdsayPathCandidate> shortlist = selectedByRoute.values()
			.stream()
			.sorted(recommendedComparator)
			.limit(ODSAY_SHORTLIST_LIMIT)
			.toList();
		log.info(
			"transit odsay shortlist odsayPathCount={} shortlistCount={} busLegCount={} walkLegCount={}",
			paths.size(),
			shortlist.size(),
			odsayBusLegCount(shortlist),
			odsayWalkLegCount(shortlist));
		return shortlist;
	}

	private java.util.Optional<TransitRouteCandidate> toCandidate(
		String searchId,
		int routeIndex,
		GeoPointRequest startPoint,
		GeoPointRequest endPoint,
		OdsayTransitPath path,
		List<OdsayLaneGeometry> laneGeometries,
		WalkRouteUserProfile profile,
		boolean enrichBims) {
		String routeId = "%s_%03d".formatted(searchId, routeIndex);
		List<RouteLegResponse> legs = toLegs(
			routeIndex,
			startPoint,
			endPoint,
			path.legs(),
			laneGeometries,
			profile,
			enrichBims);
		if (legs.isEmpty()) {
			return java.util.Optional.empty();
		}
		List<RouteLegResponse> bufferedLegs = withTransitTimeBuffers(legs, profile);
		List<RouteLegResponse> offsetLegs = withRouteGuidanceOffsets(bufferedLegs);
		if (hasMissingGeometry(offsetLegs)) {
			return java.util.Optional.empty();
		}
		String geometry = mergeGeometry(offsetLegs);
		if (geometry == null || geometry.isBlank()) {
			return java.util.Optional.empty();
		}
		int transferCount = transferCount(path);
		int durationSecond = totalDurationSecond(offsetLegs);
		RouteSummaryResponse route = new RouteSummaryResponse(
			routeId,
			TransportMode.PUBLIC_TRANSIT,
			RouteOption.RECOMMENDED,
			List.of(RouteOption.RECOMMENDED),
			title(path.legs()),
			scale(path.totalDistanceMeter()),
			durationSecond,
			estimatedMinute(durationSecond),
			routeBadges(offsetLegs),
			routeWarnings(offsetLegs, profile),
			geometry,
			offsetLegs);
		return java.util.Optional.of(new TransitRouteCandidate(
			route,
			new TransitRouteSnapshot(routeId, path.mapObj(), snapshotLegs(path)),
			path.totalWalkMeter(),
			transferCount));
	}

	private boolean hasMissingGeometry(List<RouteLegResponse> legs) {
		return legs.stream().anyMatch(leg -> leg.geometry() == null || leg.geometry().isBlank());
	}

	private List<RouteLegResponse> withTransitTimeBuffers(List<RouteLegResponse> legs, WalkRouteUserProfile profile) {
		List<RouteLegResponse> bufferedLegs = new ArrayList<>();
		boolean hasPreviousTransit = false;
		for (RouteLegResponse leg : legs) {
			int bufferSecond = 0;
			if (leg.type() == TransportMode.BUS || leg.type() == TransportMode.SUBWAY) {
				if (hasPreviousTransit) {
					bufferSecond += transitTransferBufferSecond(leg.type(), profile);
				}
				if (leg.type() == TransportMode.BUS) {
					bufferSecond += busBoardingPrepSecond(profile);
				}
				hasPreviousTransit = true;
			}
			bufferedLegs.add(bufferSecond == 0 ? leg : withAdditionalDuration(leg, bufferSecond));
		}
		return List.copyOf(bufferedLegs);
	}

	private RouteLegResponse withAdditionalDuration(RouteLegResponse leg, int additionalDurationSecond) {
		int durationSecond = leg.durationSecond() + additionalDurationSecond;
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
					event.durationFromLegStartSecond() + additionalDurationSecond,
					event.geometry()))
				.toList();
		return new RouteLegResponse(
			leg.sequence(),
			leg.type(),
			leg.role(),
			leg.instruction(),
			leg.distanceMeter(),
			durationSecond,
			estimatedMinute(durationSecond),
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

	private List<RouteLegResponse> withRouteGuidanceOffsets(List<RouteLegResponse> legs) {
		List<RouteLegResponse> offsetLegs = new ArrayList<>();
		BigDecimal distanceOffset = BigDecimal.ZERO.setScale(2);
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
		List<RouteGuidanceEventResponse> guidanceEvents = leg.guidanceEvents()
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

	private List<TransitRouteCandidate> selectCandidates(List<TransitRouteCandidate> candidates) {
		Map<String, SelectedTransitRoute> selectedByRoute = new LinkedHashMap<>();
		selectBy(candidates, this::recommendedComparator)
			.ifPresent(candidate -> addSelected(selectedByRoute, candidate, RouteOption.RECOMMENDED));
		selectBy(candidates, this::minTransferComparator)
			.ifPresent(candidate -> addSelected(selectedByRoute, candidate, RouteOption.MIN_TRANSFER));
		selectBy(candidates, this::minWalkComparator)
			.ifPresent(candidate -> addSelected(selectedByRoute, candidate, RouteOption.MIN_WALK));
		return selectedByRoute.values()
			.stream()
			.limit(3)
			.map(SelectedTransitRoute::toCandidate)
			.toList();
	}

	private List<TransitRouteBaseCandidate> selectStaticFinalCandidates(List<TransitRouteBaseCandidate> candidates) {
		List<TransitRouteCandidate> selectedCandidates = selectCandidates(
			candidates.stream().map(TransitRouteBaseCandidate::candidate).toList());
		Map<String, TransitRouteBaseCandidate> baseCandidateByRouteId = new LinkedHashMap<>();
		for (TransitRouteBaseCandidate candidate : candidates) {
			baseCandidateByRouteId.putIfAbsent(candidate.candidate().route().routeId(), candidate);
		}
		List<TransitRouteBaseCandidate> staticSelectedCandidates = selectedCandidates.stream()
			.map(candidate -> withSelectedCandidate(baseCandidateByRouteId.get(candidate.route().routeId()), candidate))
			.toList();
		log.info(
			"transit static final odsayCandidateCount={} staticFinalCount={} busLegCount={} busLaneOptionCount={}",
			candidates.size(),
			staticSelectedCandidates.size(),
			busLegCount(staticSelectedCandidates),
			busLaneOptionCount(staticSelectedCandidates));
		return staticSelectedCandidates;
	}

	private TransitRouteBaseCandidate withSelectedCandidate(
		TransitRouteBaseCandidate baseCandidate,
		TransitRouteCandidate selectedCandidate) {
		if (baseCandidate == null) {
			throw new RouteException(RouteErrorCode.ROUTE_NOT_FOUND);
		}
		return new TransitRouteBaseCandidate(baseCandidate.routeIndex(), baseCandidate.path(), selectedCandidate);
	}

	private List<TransitRouteCandidate> enrichBimsCandidates(
		List<TransitRouteBaseCandidate> baseCandidates,
		WalkRouteUserProfile profile) {
		Map<BimsArrivalKey, CompletableFuture<BusanBimsArrival>> arrivalFutures = bimsArrivalFutures(baseCandidates);
		List<TransitRouteCandidate> candidates = new ArrayList<>();
		RouteException firstBimsFailure = null;
		for (TransitRouteBaseCandidate baseCandidate : baseCandidates) {
			try {
				candidates.add(enrichBims(baseCandidate, profile, arrivalFutures));
			} catch (CompletionException exception) {
				RouteException routeException = routeException(exception);
				if (routeException == null) {
					throw exception;
				}
				if (routeException.getErrorCode() == RouteErrorCode.EXTERNAL_ROUTE_API_TIMEOUT) {
					throw routeException;
				}
				if (routeException.getErrorCode() == RouteErrorCode.EXTERNAL_ROUTE_API_FAILED
					&& firstBimsFailure == null) {
					firstBimsFailure = routeException;
				}
				log.warn(
					"transit candidate skipped provider={} operation={} routeIndex={} legIndex={} mapObj={} status={} message={}",
					"bims",
					"enrichment",
					baseCandidate.routeIndex(),
					"-",
					baseCandidate.path().mapObj(),
					routeException.getErrorCode().getStatus(),
					routeException.getMessage(),
					routeException);
			}
		}
		if (candidates.isEmpty() && firstBimsFailure != null) {
			throw firstBimsFailure;
		}
		return candidates;
	}

	private Map<BimsArrivalKey, CompletableFuture<BusanBimsArrival>> bimsArrivalFutures(
		List<TransitRouteBaseCandidate> baseCandidates) {
		Map<BimsArrivalKey, CompletableFuture<BusanBimsArrival>> arrivalFutures = new LinkedHashMap<>();
		for (TransitRouteBaseCandidate baseCandidate : baseCandidates) {
			for (OdsayTransitLeg leg : baseCandidate.path().legs()) {
				if (leg.type() != TransportMode.BUS) {
					continue;
				}
				for (OdsayTransitLane lane : leg.lanes()) {
					BimsArrivalKey key = BimsArrivalKey.of(boardingStopId(leg), lane.busLocalBlId(), lane.busNo());
					if (key.hasStopId()) {
						arrivalFutures.computeIfAbsent(key, this::bimsArrivalFuture);
					}
				}
			}
		}
		log.info("transit bims dedupe uniqueArrivalRequestCount={}", arrivalFutures.size());
		return Map.copyOf(arrivalFutures);
	}

	private CompletableFuture<BusanBimsArrival> bimsArrivalFuture(BimsArrivalKey key) {
		return CompletableFuture
			.supplyAsync(() -> busanBimsClient.findArrival(key.stopId(), key.lineId(), key.routeNo()), bimsTaskExecutor)
			.orTimeout(BIMS_ENRICHMENT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
	}

	private RouteException routeException(CompletionException exception) {
		Throwable cause = exception.getCause();
		if (cause instanceof RouteException routeException) {
			return routeException;
		}
		if (cause instanceof TimeoutException) {
			return new RouteException(RouteErrorCode.EXTERNAL_ROUTE_API_TIMEOUT);
		}
		return null;
	}

	private void addOdsayShortlistCandidates(
		Map<String, OdsayPathCandidate> selectedByRoute,
		List<OdsayPathCandidate> candidates,
		java.util.function.Supplier<Comparator<OdsayPathCandidate>> comparatorSupplier) {
		Comparator<OdsayPathCandidate> comparator = comparatorSupplier.get();
		candidates.stream()
			.sorted(comparator)
			.limit(OPTION_PRESELECT_LIMIT)
			.forEach(candidate -> selectedByRoute.putIfAbsent(routeKey(candidate.path()), candidate));
	}

	private Comparator<OdsayPathCandidate> odsayRecommendedComparator() {
		return Comparator.comparingInt((OdsayPathCandidate candidate) -> candidate.path().totalTimeMinute())
			.thenComparingInt(candidate -> transferCount(candidate.path()))
			.thenComparingInt(candidate -> candidate.path().totalWalkMeter());
	}

	private Comparator<OdsayPathCandidate> odsayMinTransferComparator() {
		return Comparator.comparingInt((OdsayPathCandidate candidate) -> transferCount(candidate.path()))
			.thenComparingInt(candidate -> candidate.path().totalTimeMinute())
			.thenComparingInt(candidate -> candidate.path().totalWalkMeter());
	}

	private Comparator<OdsayPathCandidate> odsayMinWalkComparator() {
		return Comparator.comparingInt((OdsayPathCandidate candidate) -> candidate.path().totalWalkMeter())
			.thenComparingInt(candidate -> candidate.path().totalTimeMinute())
			.thenComparingInt(candidate -> transferCount(candidate.path()));
	}

	private int odsayBusLegCount(List<OdsayPathCandidate> candidates) {
		return candidates.stream()
			.flatMap(candidate -> candidate.path().legs().stream())
			.filter(leg -> leg.type() == TransportMode.BUS)
			.mapToInt(leg -> 1)
			.sum();
	}

	private int odsayWalkLegCount(List<OdsayPathCandidate> candidates) {
		return candidates.stream()
			.flatMap(candidate -> candidate.path().legs().stream())
			.filter(leg -> leg.type() == TransportMode.WALK)
			.mapToInt(leg -> 1)
			.sum();
	}

	private int busLegCount(List<TransitRouteBaseCandidate> candidates) {
		return candidates.stream()
			.map(TransitRouteBaseCandidate::candidate)
			.flatMap(candidate -> candidate.route().legs().stream())
			.filter(leg -> leg.type() == TransportMode.BUS)
			.mapToInt(leg -> 1)
			.sum();
	}

	private int busLaneOptionCount(List<TransitRouteBaseCandidate> candidates) {
		return candidates.stream()
			.map(TransitRouteBaseCandidate::candidate)
			.flatMap(candidate -> candidate.route().legs().stream())
			.filter(leg -> leg.type() == TransportMode.BUS)
			.mapToInt(leg -> leg.laneOptions().size())
			.sum();
	}

	private java.util.Optional<TransitRouteCandidate> selectBy(
		List<TransitRouteCandidate> candidates,
		java.util.function.Supplier<Comparator<TransitRouteCandidate>> comparatorSupplier) {
		return candidates.stream().min(comparatorSupplier.get());
	}

	private Comparator<TransitRouteCandidate> recommendedComparator() {
		return Comparator.comparing(this::hasLowFloorBusForEveryBusLeg).reversed()
			.thenComparing(candidate -> candidate.route().durationSecond())
			.thenComparing(TransitRouteCandidate::transferCount)
			.thenComparing(TransitRouteCandidate::totalWalkMeter);
	}

	private Comparator<TransitRouteCandidate> baseRecommendedComparator() {
		return Comparator.comparing((TransitRouteCandidate candidate) -> candidate.route().durationSecond())
			.thenComparing(TransitRouteCandidate::transferCount)
			.thenComparing(TransitRouteCandidate::totalWalkMeter);
	}

	private Comparator<TransitRouteCandidate> minTransferComparator() {
		return Comparator.comparingInt(TransitRouteCandidate::transferCount)
			.thenComparing(candidate -> candidate.route().durationSecond())
			.thenComparing(TransitRouteCandidate::totalWalkMeter);
	}

	private Comparator<TransitRouteCandidate> minWalkComparator() {
		return Comparator.comparingInt(TransitRouteCandidate::totalWalkMeter)
			.thenComparing(candidate -> candidate.route().durationSecond())
			.thenComparing(TransitRouteCandidate::transferCount);
	}

	private void addSelected(
		Map<String, SelectedTransitRoute> selectedByRoute,
		TransitRouteCandidate candidate,
		RouteOption routeOption) {
		selectedByRoute.computeIfAbsent(routeKey(candidate.route()), key -> new SelectedTransitRoute(candidate))
			.add(routeOption);
	}

	private String routeKey(RouteSummaryResponse route) {
		List<String> transitLegKeys = route.legs()
			.stream()
			.filter(leg -> leg.type() == TransportMode.BUS || leg.type() == TransportMode.SUBWAY)
			.map(leg -> "%s:%s:%s:%s".formatted(
				leg.type(),
				leg.routeNo(),
				stopKey(leg.boardingStop()),
				stopKey(leg.arrivingStop())))
			.toList();
		if (transitLegKeys.isEmpty()) {
			return route.routeId();
		}
		return String.join("|", transitLegKeys);
	}

	private String routeKey(OdsayTransitPath path) {
		List<String> transitLegKeys = path.legs()
			.stream()
			.filter(leg -> leg.type() == TransportMode.BUS || leg.type() == TransportMode.SUBWAY)
			.map(leg -> "%s:%s:%s:%s".formatted(
				leg.type(),
				routeNo(leg),
				odsayStopKey(leg.startName(), leg.startId(), leg.startLocalStationId(), leg.startArsId()),
				odsayStopKey(leg.endName(), leg.endId(), leg.endLocalStationId(), leg.endArsId())))
			.toList();
		if (transitLegKeys.isEmpty()) {
			return path.mapObj();
		}
		return String.join("|", transitLegKeys);
	}

	private String odsayStopKey(String name, String odsayStationId, String localStationId, String arsId) {
		return "%s:%s:%s:%s".formatted(
			name == null ? "" : name,
			odsayStationId == null ? "" : odsayStationId,
			localStationId == null ? "" : localStationId,
			arsId == null ? "" : arsId);
	}

	private String stopKey(RouteStopResponse stop) {
		if (stop == null) {
			return "";
		}
		return "%s:%s:%s".formatted(stop.name(), stop.lat(), stop.lng());
	}

	private boolean hasLowFloorBusForEveryBusLeg(TransitRouteCandidate candidate) {
		List<RouteLegResponse> busLegs = candidate.route()
			.legs()
			.stream()
			.filter(leg -> leg.type() == TransportMode.BUS)
			.toList();
		return !busLegs.isEmpty() && busLegs.stream().allMatch(this::hasLowFloorBus);
	}

	private boolean hasLowFloorBus(RouteLegResponse leg) {
		return leg.laneOptions()
			.stream()
			.anyMatch(option -> Boolean.TRUE.equals(option.isLowFloor()));
	}

	private List<RouteWarningCode> routeWarnings(List<RouteLegResponse> legs, WalkRouteUserProfile profile) {
		if (!isWheelchairUser(profile) || legs.stream().noneMatch(leg -> leg.type() == TransportMode.BUS)) {
			return List.of();
		}
		boolean hasLowFloorBusForEveryBusLeg = legs.stream()
			.filter(leg -> leg.type() == TransportMode.BUS)
			.allMatch(this::hasLowFloorBus);
		if (hasLowFloorBusForEveryBusLeg) {
			return List.of();
		}
		return List.of(RouteWarningCode.LOW_FLOOR_BUS_UNAVAILABLE);
	}

	private int totalDurationSecond(List<RouteLegResponse> legs) {
		return legs.stream().mapToInt(RouteLegResponse::durationSecond).sum();
	}

	private int transitTransferBufferSecond(TransportMode nextTransitType, WalkRouteUserProfile profile) {
		if (nextTransitType == TransportMode.SUBWAY) {
			return isWheelchairUser(profile) ? WHEELCHAIR_SUBWAY_TRANSFER_BUFFER_SECOND : SUBWAY_TRANSFER_BUFFER_SECOND;
		}
		return BUS_TRANSFER_BUFFER_SECOND;
	}

	private int busBoardingPrepSecond(WalkRouteUserProfile profile) {
		if (profile.primaryUserType() == PrimaryUserType.LOW_VISION || isWheelchairUser(profile)) {
			return ACCESSIBLE_BUS_BOARDING_PREP_SECOND;
		}
		return DEFAULT_BUS_BOARDING_PREP_SECOND;
	}

	private boolean isWheelchairUser(WalkRouteUserProfile profile) {
		return profile.primaryUserType() == PrimaryUserType.MOBILITY_IMPAIRED
			&& (profile.mobilitySubtype() == MobilitySubtype.POWER_WHEELCHAIR
				|| profile.mobilitySubtype() == MobilitySubtype.MANUAL_WHEELCHAIR);
	}

	private TransitRouteCandidate enrichBims(
		TransitRouteBaseCandidate baseCandidate,
		WalkRouteUserProfile profile,
		Map<BimsArrivalKey, CompletableFuture<BusanBimsArrival>> arrivalFutures) {
		TransitRouteCandidate candidate = baseCandidate.candidate();
		RouteSummaryResponse route = candidate.route();
		List<RouteLegResponse> enrichedLegs = enrichBimsLegs(
			route.legs(),
			baseCandidate.path().legs(),
			baseCandidate.routeIndex(),
			arrivalFutures);
		RouteSummaryResponse enrichedRoute = new RouteSummaryResponse(
			route.routeId(),
			route.transportMode(),
			route.routeOption(),
			route.routeOptions(),
			route.title(),
			route.distanceMeter(),
			route.durationSecond(),
			route.estimatedTimeMinute(),
			route.badges(),
			routeWarnings(enrichedLegs, profile),
			route.geometry(),
			enrichedLegs);
		return new TransitRouteCandidate(
			enrichedRoute,
			candidate.snapshot(),
			candidate.totalWalkMeter(),
			candidate.transferCount());
	}

	private List<RouteLegResponse> enrichBimsLegs(
		List<RouteLegResponse> legs,
		List<OdsayTransitLeg> odsayLegs,
		int routeIndex,
		Map<BimsArrivalKey, CompletableFuture<BusanBimsArrival>> arrivalFutures) {
		List<RouteLegResponse> enrichedLegs = new ArrayList<>();
		for (RouteLegResponse leg : legs) {
			if (leg.type() != TransportMode.BUS) {
				enrichedLegs.add(leg);
				continue;
			}
			int odsayIndex = leg.sequence() - 1;
			if (odsayIndex < 0 || odsayIndex >= odsayLegs.size()) {
				enrichedLegs.add(leg);
				continue;
			}
			OdsayTransitLeg odsayLeg = odsayLegs.get(odsayIndex);
			if (odsayLeg.type() != TransportMode.BUS) {
				enrichedLegs.add(leg);
				continue;
			}
			enrichedLegs.add(enrichBimsLeg(leg, odsayLeg, routeIndex, arrivalFutures));
		}
		return List.copyOf(enrichedLegs);
	}

	private RouteLegResponse enrichBimsLeg(
		RouteLegResponse leg,
		OdsayTransitLeg odsayLeg,
		int routeIndex,
		Map<BimsArrivalKey, CompletableFuture<BusanBimsArrival>> arrivalFutures) {
		List<TransitLaneOptionResponse> laneOptions = laneOptions(
			routeIndex,
			leg.sequence(),
			odsayLeg,
			true,
			arrivalFutures);
		String routeNo = routeNo(odsayLeg, laneOptions);
		return new RouteLegResponse(
			leg.sequence(),
			leg.type(),
			leg.role(),
			instruction(odsayLeg, routeNo),
			leg.distanceMeter(),
			leg.durationSecond(),
			leg.estimatedTimeMinute(),
			leg.geometry(),
			leg.guidanceEvents(),
			routeNo,
			laneOptions,
			leg.boardingStop(),
			leg.arrivingStop(),
			leg.remainingMinute(),
			leg.headsign(),
			representativeLowFloor(laneOptions),
			leg.badges());
	}

	private Boolean representativeLowFloor(List<TransitLaneOptionResponse> laneOptions) {
		if (laneOptions == null || laneOptions.isEmpty()) {
			return null;
		}
		return laneOptions.get(0).isLowFloor();
	}

	private record OdsayPathCandidate(
		int routeIndex,
		OdsayTransitPath path) {
	}

	private record TransitRouteBaseCandidate(
		int routeIndex,
		OdsayTransitPath path,
		TransitRouteCandidate candidate) {
	}

	private record BimsArrivalKey(
		String stopId,
		String lineId,
		String routeNo) {

		private static BimsArrivalKey of(String stopId, String lineId, String routeNo) {
			return new BimsArrivalKey(blankToNull(stopId), blankToNull(lineId), blankToNull(routeNo));
		}

		private boolean hasStopId() {
			return stopId != null;
		}

		private static String blankToNull(String value) {
			if (value == null || value.isBlank()) {
				return null;
			}
			return value.trim();
		}
	}

	private record SelectedTransitRoute(
		TransitRouteCandidate candidate,
		Set<RouteOption> routeOptions) {

		private SelectedTransitRoute(TransitRouteCandidate candidate) {
			this(candidate, new LinkedHashSet<>());
		}

		private void add(RouteOption routeOption) {
			routeOptions.add(routeOption);
		}

		private TransitRouteCandidate toCandidate() {
			List<RouteOption> options = List.copyOf(routeOptions);
			RouteSummaryResponse route = candidate.route();
			RouteSummaryResponse routeWithOptions = new RouteSummaryResponse(
				route.routeId(),
				route.transportMode(),
				options.get(0),
				options,
				route.title(),
				route.distanceMeter(),
				route.durationSecond(),
				route.estimatedTimeMinute(),
				route.badges(),
				route.warnings(),
				route.geometry(),
				route.legs());
			return new TransitRouteCandidate(
				routeWithOptions,
				candidate.snapshot(),
				candidate.totalWalkMeter(),
				candidate.transferCount());
		}
	}

	private List<RouteLegResponse> toLegs(
		int routeIndex,
		GeoPointRequest startPoint,
		GeoPointRequest endPoint,
		List<OdsayTransitLeg> odsayLegs,
		List<OdsayLaneGeometry> laneGeometries,
		WalkRouteUserProfile profile,
		boolean enrichBims) {
		List<RouteLegResponse> legs = new ArrayList<>();
		GeoPointRequest cursor = startPoint;
		int[] geometryIndex = {0};
		for (int odsayIndex = 0; odsayIndex < odsayLegs.size(); odsayIndex++) {
			OdsayTransitLeg odsayLeg = odsayLegs.get(odsayIndex);
			int legIndex = legs.size() + 1;
			try {
				if (odsayLeg.type() == TransportMode.WALK) {
					TransportMode nextTransitType = nextTransitType(odsayLegs, odsayIndex);
					RouteStopResponse nextTransitStop = nextTransitStop(odsayLegs, odsayIndex, cursor);
					GeoPointRequest nextPoint = nextTransitStop == null
						? endPoint
						: new GeoPointRequest(
							nextTransitStop.lat().doubleValue(),
							nextTransitStop.lng().doubleValue());
					RouteLegResponse walkLeg = toWalkLeg(
						legIndex,
						cursor,
						nextPoint,
						nextTransitType,
						nextTransitStop,
						profile);
					if (walkLeg == null) {
						return List.of();
					}
					legs.add(walkLeg);
					cursor = nextPoint;
					continue;
				}
				RouteLegResponse transitLeg = toTransitLeg(
					legIndex,
					routeIndex,
					odsayLeg,
					cursor,
					nextGeometry(odsayLeg.type(), laneGeometries, geometryIndex),
					enrichBims);
				legs.add(transitLeg);
				if (transitLeg.arrivingStop() != null) {
					cursor = new GeoPointRequest(
						transitLeg.arrivingStop().lat().doubleValue(),
						transitLeg.arrivingStop().lng().doubleValue());
				}
			} catch (RouteException exception) {
				log.warn(
					"transit leg failed provider={} operation={} routeIndex={} legIndex={} status={} message={}",
					provider(odsayLeg),
					operation(odsayLeg),
					routeIndex,
					legIndex,
					exception.getErrorCode().getStatus(),
					exception.getMessage());
				throw exception;
			}
		}
		return List.copyOf(legs);
	}

	private RouteStopResponse nextTransitStop(
		List<OdsayTransitLeg> odsayLegs,
		int currentIndex,
		GeoPointRequest referencePoint) {
		for (int index = currentIndex + 1; index < odsayLegs.size(); index++) {
			OdsayTransitLeg next = odsayLegs.get(index);
			if (next.type() != TransportMode.WALK) {
				return boardingStop(next, referencePoint);
			}
		}
		return null;
	}

	private TransportMode nextTransitType(List<OdsayTransitLeg> odsayLegs, int currentIndex) {
		for (int index = currentIndex + 1; index < odsayLegs.size(); index++) {
			TransportMode type = odsayLegs.get(index).type();
			if (type != TransportMode.WALK) {
				return type;
			}
		}
		return null;
	}

	private RouteLegResponse toWalkLeg(
		int sequence,
		GeoPointRequest from,
		GeoPointRequest to,
		TransportMode nextTransitType,
		RouteStopResponse nextTransitStop,
		WalkRouteUserProfile profile) {
		boolean hasNextTransit = nextTransitType != null;
		String instruction = walkInstruction(nextTransitType, nextTransitStop);
		if (GeoDistanceCalculator.distanceMeter(from, to) < 1.0) {
			return new RouteLegResponse(
				sequence,
				TransportMode.WALK,
				walkRole(hasNextTransit),
				instruction,
				BigDecimal.ZERO.setScale(2),
				0,
				0,
				lineString(from, to),
				zeroDistanceGuidanceEvents(from, nextTransitType),
				null,
				List.of(),
				null,
				null,
				null,
				List.of());
		}
		try {
			WalkRouteProfile resolvedProfile = walkRouteProfileService.resolve(
				profile.primaryUserType(),
				profile.mobilitySubtype(),
				RouteOption.SAFE);
			GraphHopperRoutePath path = graphHopperRouteClient.route(new GraphHopperRouteRequest(
				from,
				to,
				resolvedProfile,
				false));
			return walkRoutePayloadService.toWalkLeg(
				sequence,
				walkRole(hasNextTransit),
				instruction,
				path,
				resolvedProfile,
				null,
				destinationEventType(nextTransitType));
		} catch (RouteException exception) {
			if (exception.getErrorCode() == RouteErrorCode.ROUTE_NOT_FOUND) {
				return null;
			}
			throw exception;
		}
	}

	private List<RouteGuidanceEventResponse> zeroDistanceGuidanceEvents(
		GeoPointRequest point,
		TransportMode nextTransitType) {
		List<RouteGuidanceEventType> eventTypes = new ArrayList<>();
		eventTypes.add(destinationEventType(nextTransitType));
		List<RouteGuidanceEventResponse> events = new ArrayList<>();
		for (int index = 0; index < eventTypes.size(); index++) {
			events.add(new RouteGuidanceEventResponse(
				index + 1,
				eventTypes.get(index),
				BigDecimal.ZERO.setScale(2),
				0,
				point(point)));
		}
		return List.copyOf(events);
	}

	private String point(GeoPointRequest point) {
		return "POINT(" + BigDecimal.valueOf(point.lng()).toPlainString()
			+ " " + BigDecimal.valueOf(point.lat()).toPlainString() + ")";
	}

	private String point(RouteStopResponse stop) {
		return "POINT(" + stop.lng().toPlainString() + " " + stop.lat().toPlainString() + ")";
	}

	private RouteGuidanceEventType destinationEventType(TransportMode nextTransitType) {
		if (nextTransitType == TransportMode.BUS) {
			return RouteGuidanceEventType.BUS_STOP;
		}
		if (nextTransitType == TransportMode.SUBWAY) {
			return RouteGuidanceEventType.SUBWAY_ELEVATOR;
		}
		return RouteGuidanceEventType.DESTINATION;
	}

	private RouteLegResponse toTransitLeg(
		int sequence,
		int routeIndex,
		OdsayTransitLeg odsayLeg,
		GeoPointRequest referencePoint,
		String geometry,
		boolean enrichBims) {
		int durationSecond = Math.max(0, odsayLeg.sectionTimeMinute() * 60);
		List<TransitLaneOptionResponse> laneOptions = laneOptions(routeIndex, sequence, odsayLeg, enrichBims);
		String routeNo = routeNo(odsayLeg, laneOptions);
		RouteStopResponse boardingStop = boardingStop(odsayLeg, referencePoint);
		RouteStopResponse arrivingStop = arrivingStop(odsayLeg);
		SubwayDepartureInfo subwayDeparture = subwayDepartureInfo(odsayLeg);
		BigDecimal distanceMeter = scale(odsayLeg.distanceMeter());
		return new RouteLegResponse(
			sequence,
			odsayLeg.type(),
			RouteLegRole.TRANSIT,
			instruction(odsayLeg, routeNo),
			distanceMeter,
			durationSecond,
			estimatedMinute(durationSecond),
			geometry != null ? geometry
				: lineString(odsayLeg.startLng(), odsayLeg.startLat(), odsayLeg.endLng(), odsayLeg.endLat()),
			arrivingPointEvents(arrivingStop, distanceMeter, durationSecond),
			routeNo,
			laneOptions,
			boardingStop,
			arrivingStop,
			subwayRemainingMinute(subwayDeparture),
			subwayHeadsign(subwayDeparture),
			odsayLeg.type() == TransportMode.BUS ? representativeLowFloor(laneOptions) : null,
			odsayLeg.type() == TransportMode.SUBWAY ? List.of(RouteBadge.ELEVATOR) : List.of());
	}

	private List<RouteGuidanceEventResponse> arrivingPointEvents(
		RouteStopResponse arrivingStop,
		BigDecimal distanceMeter,
		int durationSecond) {
		if (arrivingStop == null) {
			return List.of();
		}
		return List.of(new RouteGuidanceEventResponse(
			1,
			RouteGuidanceEventType.ARRIVING_POINT,
			distanceMeter == null ? BigDecimal.ZERO.setScale(2) : distanceMeter,
			durationSecond,
			point(arrivingStop)));
	}

	private RouteStopResponse boardingStop(OdsayTransitLeg leg, GeoPointRequest referencePoint) {
		if (leg.type() == TransportMode.SUBWAY) {
			return subwayStop(leg.startName(), leg.startId(), leg.startExitLat(), leg.startExitLng(), referencePoint);
		}
		return stop(leg.startName(), leg.startLat(), leg.startLng());
	}

	private RouteStopResponse arrivingStop(OdsayTransitLeg leg) {
		if (leg.type() == TransportMode.SUBWAY) {
			return subwayStop(leg.endName(), leg.endId(), leg.endExitLat(), leg.endExitLng(),
				referencePoint(leg.endLat(), leg.endLng()));
		}
		return stop(leg.endName(), leg.endLat(), leg.endLng());
	}

	private RouteStopResponse subwayStop(String name, String odsayStationId, BigDecimal fallbackLat,
		BigDecimal fallbackLng, GeoPointRequest referencePoint) {
		List<SubwayStationElevator> elevators = subwayStationElevatorRepository.findByOdsayStationId(odsayStationId);
		if (!elevators.isEmpty()) {
			SubwayStationElevator elevator = nearestElevator(elevators, referencePoint);
			return stop(elevator.getStationName(), elevator);
		}
		return subwayStationRepository.findByOdsayStationId(odsayStationId)
			.filter(station -> station.getPoint() != null)
			.map(station -> stop(station.getStationName(), station))
			.orElseGet(() -> stop(name, fallbackLat, fallbackLng));
	}

	private SubwayStationElevator nearestElevator(List<SubwayStationElevator> elevators,
		GeoPointRequest referencePoint) {
		if (referencePoint == null) {
			return elevators.get(0);
		}
		return elevators.stream()
			.min(Comparator.comparingDouble(elevator -> GeoDistanceCalculator.distanceMeter(
				referencePoint,
				new GeoPointRequest(elevator.getPoint().getY(), elevator.getPoint().getX()))))
			.orElse(elevators.get(0));
	}

	private GeoPointRequest referencePoint(BigDecimal lat, BigDecimal lng) {
		if (lat == null || lng == null) {
			return null;
		}
		return new GeoPointRequest(lat.doubleValue(), lng.doubleValue());
	}

	private RouteStopResponse stop(String name, SubwayStation station) {
		return new RouteStopResponse(
			name,
			BigDecimal.valueOf(station.getPoint().getY()),
			BigDecimal.valueOf(station.getPoint().getX()));
	}

	private RouteStopResponse stop(String name, SubwayStationElevator elevator) {
		return new RouteStopResponse(
			name,
			BigDecimal.valueOf(elevator.getPoint().getY()),
			BigDecimal.valueOf(elevator.getPoint().getX()));
	}

	private String nextGeometry(
		TransportMode type,
		List<OdsayLaneGeometry> laneGeometries,
		int[] geometryIndex) {
		for (int index = geometryIndex[0]; index < laneGeometries.size(); index++) {
			OdsayLaneGeometry geometry = laneGeometries.get(index);
			geometryIndex[0] = index + 1;
			if (geometry.type() == type) {
				return geometry.geometry();
			}
		}
		return null;
	}

	private RouteLegRole walkRole(boolean hasNextTransit) {
		if (hasNextTransit) {
			return RouteLegRole.WALK_TO_TRANSIT;
		}
		return RouteLegRole.TRANSIT_TO_WALK;
	}

	private String walkInstruction(TransportMode nextTransitType, RouteStopResponse targetStop) {
		if (nextTransitType == null) {
			return "목적지까지 이동하세요.";
		}
		if (nextTransitType == TransportMode.BUS) {
			return busStopInstruction(targetStop);
		}
		if (nextTransitType == TransportMode.SUBWAY) {
			return subwayElevatorInstruction(targetStop);
		}
		return "대중교통 탑승 지점까지 이동하세요.";
	}

	private String busStopInstruction(RouteStopResponse targetStop) {
		if (targetStop == null || targetStop.name() == null || targetStop.name().isBlank()) {
			return "버스 정류장까지 이동하세요.";
		}
		String stopName = targetStop.name().endsWith("정류장") ? targetStop.name() : targetStop.name() + " 정류장";
		return stopName + "까지 이동하세요.";
	}

	private String subwayElevatorInstruction(RouteStopResponse targetStop) {
		if (targetStop == null || targetStop.name() == null || targetStop.name().isBlank()) {
			return "지하철역 엘리베이터까지 이동하세요.";
		}
		String stationName = targetStop.name().endsWith("역") ? targetStop.name() : targetStop.name() + "역";
		return stationName + " 엘리베이터까지 이동하세요.";
	}

	private List<RouteBadge> routeBadges(List<RouteLegResponse> legs) {
		Set<RouteBadge> badges = new LinkedHashSet<>();
		legs.stream()
			.flatMap(leg -> (leg.badges() == null ? List.<RouteBadge>of() : leg.badges()).stream())
			.forEach(badges::add);
		return BADGE_PRIORITY.stream()
			.filter(badges::contains)
			.toList();
	}

	private List<TransitLaneOptionResponse> laneOptions(
		int routeIndex,
		int legIndex,
		OdsayTransitLeg odsayLeg,
		boolean enrichBims) {
		return laneOptions(routeIndex, legIndex, odsayLeg, enrichBims, Map.of());
	}

	private List<TransitLaneOptionResponse> laneOptions(
		int routeIndex,
		int legIndex,
		OdsayTransitLeg odsayLeg,
		boolean enrichBims,
		Map<BimsArrivalKey, CompletableFuture<BusanBimsArrival>> arrivalFutures) {
		if (odsayLeg.type() != TransportMode.BUS) {
			return List.of();
		}
		int durationSecond = Math.max(0, odsayLeg.sectionTimeMinute() * 60);
		if (!enrichBims) {
			return odsayLeg.lanes()
				.stream()
				.map(lane -> new TransitLaneOptionResponse(
					lane.busNo(),
					null,
					durationSecond,
					estimatedMinute(durationSecond),
					null))
				.toList();
		}
		return odsayLeg.lanes()
			.stream()
			.map(lane -> laneOption(routeIndex, legIndex, odsayLeg, lane, durationSecond, arrivalFutures))
			.sorted((left, right) -> Boolean.compare(
				Boolean.TRUE.equals(right.isLowFloor()),
				Boolean.TRUE.equals(left.isLowFloor())))
			.toList();
	}

	private TransitLaneOptionResponse laneOption(
		int routeIndex,
		int legIndex,
		OdsayTransitLeg odsayLeg,
		OdsayTransitLane lane,
		int durationSecond,
		Map<BimsArrivalKey, CompletableFuture<BusanBimsArrival>> arrivalFutures) {
		String stopId = boardingStopId(odsayLeg);
		BimsArrivalKey key = BimsArrivalKey.of(stopId, lane.busLocalBlId(), lane.busNo());
		try {
			BusanBimsArrival arrival = arrivalFutures
				.getOrDefault(key, CompletableFuture.completedFuture(
					new BusanBimsArrival(stopId, lane.busLocalBlId(), lane.busNo(), null, null)))
				.join();
			return new TransitLaneOptionResponse(
				lane.busNo(),
				arrival.remainingMinute(),
				durationSecond,
				estimatedMinute(durationSecond),
				arrival.isLowFloor(),
				lowFloorReservation(odsayLeg, lane.busNo(), arrival));
		} catch (RouteException exception) {
			log.warn(
				"transit lane failed provider={} operation={} routeIndex={} legIndex={} stopId={} lineId={} routeNo={} status={} message={}",
				"bims",
				"arrival",
				routeIndex,
				legIndex,
				stopId,
				lane.busLocalBlId(),
				lane.busNo(),
				exception.getErrorCode().getStatus(),
				exception.getMessage());
			throw exception;
		}
	}

	private LowFloorBusReservationResponse lowFloorReservation(
		OdsayTransitLeg leg,
		String routeNo,
		BusanBimsArrival arrival) {
		if (!Boolean.TRUE.equals(arrival.isLowFloor()) || arrival.remainingMinute() == null) {
			return null;
		}
		String stopName = boardingStopName(leg);
		String arsNo = boardingArsNo(leg);
		if (!StringUtils.hasText(stopName)
			|| !StringUtils.hasText(arsNo)
			|| !StringUtils.hasText(routeNo)
			|| !StringUtils.hasText(arrival.vehicleNo())) {
			return null;
		}
		return new LowFloorBusReservationResponse(
			stopName,
			arsNo,
			routeNo,
			arrival.vehicleNo(),
			arrival.remainingMinute(),
			arrival.remainingStopCount());
	}

	private String boardingStopName(OdsayTransitLeg leg) {
		if (!leg.passStops().isEmpty() && StringUtils.hasText(leg.passStops().get(0).stationName())) {
			return leg.passStops().get(0).stationName();
		}
		return leg.startName();
	}

	private String boardingArsNo(OdsayTransitLeg leg) {
		if (!leg.passStops().isEmpty() && StringUtils.hasText(leg.passStops().get(0).arsId())) {
			return leg.passStops().get(0).arsId();
		}
		return leg.startArsId();
	}

	private String provider(OdsayTransitLeg odsayLeg) {
		return switch (odsayLeg.type()) {
			case WALK -> "graphhopper";
			case BUS -> "bims";
			case SUBWAY -> "route";
			default -> "route";
		};
	}

	private String operation(OdsayTransitLeg odsayLeg) {
		return switch (odsayLeg.type()) {
			case WALK -> "walkRoute";
			case BUS -> "busArrival";
			case SUBWAY -> "transitLeg";
			default -> "transitLeg";
		};
	}

	private String boardingStopId(OdsayTransitLeg leg) {
		if (!leg.passStops().isEmpty() && leg.passStops().get(0).localStationId() != null) {
			return leg.passStops().get(0).localStationId();
		}
		return leg.startLocalStationId();
	}

	private RouteStopResponse stop(String name, BigDecimal lat, BigDecimal lng) {
		if (lat == null || lng == null) {
			return null;
		}
		return new RouteStopResponse(name, lat, lng);
	}

	private String instruction(OdsayTransitLeg leg, String routeNo) {
		String displayRouteNo = routeNo == null || routeNo.isBlank() ? leg.type().name() : routeNo;
		if (leg.type() == TransportMode.BUS) {
			return busRouteDisplayName(displayRouteNo) + "에 탑승하세요.";
		}
		return displayRouteNo + "에 탑승하세요.";
	}

	private String busRouteDisplayName(String routeNo) {
		if (routeNo.endsWith("버스")) {
			return routeNo;
		}
		if (routeNo.endsWith("번")) {
			return routeNo + " 버스";
		}
		return routeNo + "번 버스";
	}

	private String routeNo(OdsayTransitLeg leg) {
		return leg.lanes()
			.stream()
			.map(lane -> leg.type() == TransportMode.BUS ? lane.busNo() : lane.name())
			.filter(value -> value != null && !value.isBlank())
			.findFirst()
			.orElse(leg.type().name());
	}

	private String routeNo(OdsayTransitLeg leg, List<TransitLaneOptionResponse> laneOptions) {
		if (leg.type() == TransportMode.BUS && !laneOptions.isEmpty()) {
			return laneOptions.get(0).routeNo();
		}
		return routeNo(leg);
	}

	private String title(List<OdsayTransitLeg> legs) {
		List<String> routeNumbers = legs.stream()
			.filter(leg -> leg.type() == TransportMode.BUS || leg.type() == TransportMode.SUBWAY)
			.map(this::routeNo)
			.distinct()
			.toList();
		return routeNumbers.isEmpty() ? "대중교통 경로" : String.join(" / ", routeNumbers) + " 경로";
	}

	private int transferCount(OdsayTransitPath path) {
		return Math.max(0, path.busTransitCount() + path.subwayTransitCount() - 1);
	}

	private String mergeGeometry(List<RouteLegResponse> legs) {
		List<String> coordinates = new ArrayList<>();
		for (RouteLegResponse leg : legs) {
			coordinates.addAll(extractCoordinates(leg.geometry()));
		}
		if (coordinates.size() < 2) {
			return null;
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

	private String lineString(GeoPointRequest from, GeoPointRequest to) {
		return lineString(
			BigDecimal.valueOf(from.lng()),
			BigDecimal.valueOf(from.lat()),
			BigDecimal.valueOf(to.lng()),
			BigDecimal.valueOf(to.lat()));
	}

	private String lineString(BigDecimal startLng, BigDecimal startLat, BigDecimal endLng, BigDecimal endLat) {
		if (startLng == null || startLat == null || endLng == null || endLat == null) {
			return null;
		}
		return "LINESTRING(%s %s, %s %s)".formatted(startLng, startLat, endLng, endLat);
	}

	private BigDecimal scale(BigDecimal value) {
		if (value == null) {
			return null;
		}
		return value.setScale(2, RoundingMode.HALF_UP);
	}

	private int estimatedMinute(int durationSecond) {
		if (durationSecond <= 0) {
			return 0;
		}
		return Math.max(1, durationSecond / 60);
	}

	private List<Map<String, Object>> snapshotLegs(OdsayTransitPath path) {
		return path.legs()
			.stream()
			.map(this::snapshotLeg)
			.toList();
	}

	private Map<String, Object> snapshotLeg(OdsayTransitLeg leg) {
		Map<String, Object> snapshot = new LinkedHashMap<>(leg.snapshot());
		snapshot.put("type", leg.type());
		snapshot.put("lanes", leg.lanes().stream().map(this::snapshotLane).toList());
		snapshot.put("passStops", leg.passStops().stream().map(this::snapshotStop).toList());
		if (leg.type() == TransportMode.SUBWAY) {
			RouteStopResponse boardingStop = boardingStop(leg, null);
			RouteStopResponse arrivingStop = arrivingStop(leg);
			snapshot.put("odsayStationId", leg.startId());
			snapshot.put("endOdsayStationId", leg.endId());
			snapshot.put("lineName", routeNo(leg));
			snapshot.put("wayCode", leg.wayCode());
			snapshot.put("boardingElevator", snapshotStop(boardingStop));
			snapshot.put("arrivingElevator", snapshotStop(arrivingStop));
			snapshot.put("nextDeparture", nextDepartureSnapshot(leg));
		}
		return snapshot;
	}

	private Map<String, Object> snapshotLane(OdsayTransitLane lane) {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("busNo", lane.busNo());
		snapshot.put("busLocalBlID", lane.busLocalBlId());
		snapshot.put("subwayCode", lane.subwayCode());
		snapshot.put("name", lane.name());
		return snapshot;
	}

	private Map<String, Object> snapshotStop(OdsayPassStop stop) {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("stationID", stop.stationId());
		snapshot.put("stationName", stop.stationName());
		snapshot.put("localStationID", stop.localStationId());
		snapshot.put("arsID", stop.arsId());
		snapshot.put("lat", stop.lat());
		snapshot.put("lng", stop.lng());
		return snapshot;
	}

	private Map<String, Object> snapshotStop(RouteStopResponse stop) {
		if (stop == null) {
			return Map.of();
		}
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("name", stop.name());
		snapshot.put("lat", stop.lat());
		snapshot.put("lng", stop.lng());
		return snapshot;
	}

	private Map<String, Object> nextDepartureSnapshot(OdsayTransitLeg leg) {
		SubwayDepartureInfo nextDeparture = subwayDepartureInfo(leg);
		if (nextDeparture == null) {
			return Map.of();
		}
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("departureTimeText", nextDeparture.timetable().getDepartureTimeText());
		snapshot.put("departureSecondOfDay", nextDeparture.timetable().getDepartureSecondOfDay());
		snapshot.put("endStationName", nextDeparture.timetable().getEndStationName());
		return snapshot;
	}

	private SubwayDepartureInfo subwayDepartureInfo(OdsayTransitLeg leg) {
		if (leg.type() != TransportMode.SUBWAY || leg.wayCode() == null) {
			return null;
		}
		LocalDateTime now = LocalDateTime.now(SEOUL_ZONE_ID);
		int secondOfDay = now.toLocalTime().toSecondOfDay();
		SubwayServiceDayType serviceDayType = serviceDayType(now.getDayOfWeek());
		List<SubwayTimetable> departures = subwayTimetableRepository.findNextDepartures(
			leg.startId(),
			serviceDayType,
			leg.wayCode(),
			secondOfDay,
			PageRequest.of(0, 1));
		if (!departures.isEmpty()) {
			return new SubwayDepartureInfo(departures.get(0), secondOfDay, false);
		}
		SubwayServiceDayType nextServiceDayType = serviceDayType(now.toLocalDate().plusDays(1).getDayOfWeek());
		List<SubwayTimetable> firstDepartures = subwayTimetableRepository.findFirstDepartures(
			leg.startId(),
			nextServiceDayType,
			leg.wayCode(),
			PageRequest.of(0, 1));
		return firstDepartures.isEmpty() ? null : new SubwayDepartureInfo(firstDepartures.get(0), secondOfDay, true);
	}

	private Integer subwayRemainingMinute(SubwayDepartureInfo departure) {
		if (departure == null) {
			return null;
		}
		return remainingMinute(
			departure.nowSecondOfDay(),
			departure.timetable().getDepartureSecondOfDay(),
			departure.nextDay());
	}

	private String subwayHeadsign(SubwayDepartureInfo departure) {
		if (departure == null || !StringUtils.hasText(departure.timetable().getEndStationName())) {
			return null;
		}
		return departure.timetable().getEndStationName() + "행";
	}

	private int remainingMinute(int nowSecondOfDay, int departureSecondOfDay, boolean nextDay) {
		int remainSecond = departureSecondOfDay - nowSecondOfDay;
		if (nextDay || remainSecond < 0) {
			remainSecond += DAY_SECONDS;
		}
		return Math.max(0, (int)Math.ceil(remainSecond / 60.0));
	}

	private SubwayServiceDayType serviceDayType(DayOfWeek dayOfWeek) {
		if (dayOfWeek == DayOfWeek.SATURDAY) {
			return SubwayServiceDayType.SATURDAY;
		}
		if (dayOfWeek == DayOfWeek.SUNDAY) {
			return SubwayServiceDayType.HOLIDAY;
		}
		return SubwayServiceDayType.WEEKDAY;
	}

	private void validateServiceArea(GeoPointRequest startPoint, GeoPointRequest endPoint) {
		if (!isInBusan(startPoint) || !isInBusan(endPoint)) {
			throw new RouteException(RouteErrorCode.OUT_OF_SERVICE_AREA);
		}
	}

	private boolean isInBusan(GeoPointRequest point) {
		return point.lat() >= BUSAN_MIN_LAT
			&& point.lat() <= BUSAN_MAX_LAT
			&& point.lng() >= BUSAN_MIN_LNG
			&& point.lng() <= BUSAN_MAX_LNG;
	}

	private record SubwayDepartureInfo(
		SubwayTimetable timetable,
		int nowSecondOfDay,
		boolean nextDay) {
	}

	private void validateStartEndDistance(GeoPointRequest startPoint, GeoPointRequest endPoint) {
		if (GeoDistanceCalculator.distanceMeter(startPoint, endPoint) <= START_END_MIN_DISTANCE_METER) {
			throw new RouteException(RouteErrorCode.START_END_TOO_CLOSE);
		}
	}
}
