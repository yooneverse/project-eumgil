package com.ssafy.e102.domain.route.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.e102.domain.route.dto.request.TransitRefreshRequest;
import com.ssafy.e102.domain.route.dto.response.RouteLegResponse;
import com.ssafy.e102.domain.route.dto.response.RouteSummaryResponse;
import com.ssafy.e102.domain.route.dto.response.TransitArrivalResponse;
import com.ssafy.e102.domain.route.dto.response.TransitArrivalStatus;
import com.ssafy.e102.domain.route.dto.response.TransitRefreshResponse;
import com.ssafy.e102.domain.route.entity.RouteSession;
import com.ssafy.e102.domain.route.entity.SubwayTimetable;
import com.ssafy.e102.domain.route.exception.RouteErrorCode;
import com.ssafy.e102.domain.route.exception.RouteException;
import com.ssafy.e102.domain.route.repository.RouteSessionRepository;
import com.ssafy.e102.domain.route.repository.SubwayTimetableRepository;
import com.ssafy.e102.domain.route.type.RouteSessionStatus;
import com.ssafy.e102.domain.route.type.SubwayServiceDayType;
import com.ssafy.e102.domain.route.type.TransportMode;
import com.ssafy.e102.global.external.bims.BusanBimsArrival;
import com.ssafy.e102.global.external.bims.BusanBimsClient;

@Service
public class TransitRefreshService {

	private static final Logger log = LoggerFactory.getLogger(TransitRefreshService.class);
	private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");
	private static final int DAY_SECONDS = 24 * 60 * 60;

	private final RouteSessionRepository routeSessionRepository;
	private final ObjectMapper objectMapper;
	private final BimsArrivalCacheService bimsArrivalCacheService;
	private final BusanBimsClient busanBimsClient;
	private final SubwayTimetableRepository subwayTimetableRepository;
	private final TransactionOperations readOnlyTransaction;
	private final Clock clock;

	@Autowired
	public TransitRefreshService(
		RouteSessionRepository routeSessionRepository,
		ObjectMapper objectMapper,
		BimsArrivalCacheService bimsArrivalCacheService,
		BusanBimsClient busanBimsClient,
		SubwayTimetableRepository subwayTimetableRepository,
		PlatformTransactionManager transactionManager) {
		this(
			routeSessionRepository,
			objectMapper,
			bimsArrivalCacheService,
			busanBimsClient,
			subwayTimetableRepository,
			readOnlyTransaction(transactionManager),
			Clock.system(SEOUL_ZONE_ID));
	}

	TransitRefreshService(
		RouteSessionRepository routeSessionRepository,
		ObjectMapper objectMapper,
		BimsArrivalCacheService bimsArrivalCacheService,
		BusanBimsClient busanBimsClient,
		SubwayTimetableRepository subwayTimetableRepository,
		TransactionOperations readOnlyTransaction,
		Clock clock) {
		this.routeSessionRepository = routeSessionRepository;
		this.objectMapper = objectMapper;
		this.bimsArrivalCacheService = bimsArrivalCacheService;
		this.busanBimsClient = busanBimsClient;
		this.subwayTimetableRepository = subwayTimetableRepository;
		this.readOnlyTransaction = readOnlyTransaction;
		this.clock = clock;
	}

	private static TransactionOperations readOnlyTransaction(PlatformTransactionManager transactionManager) {
		TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
		transactionTemplate.setReadOnly(true);
		return transactionTemplate;
	}

	public TransitRefreshResponse refresh(UUID userId, String routeId, TransitRefreshRequest request) {
		RefreshTarget target = readOnlyTransaction
			.execute(status -> refreshTarget(userId, routeId, request.legSequence()));
		if (target == null) {
			throw new RouteException(RouteErrorCode.ROUTE_SESSION_NOT_FOUND);
		}
		TransitRefreshResponse response;
		if (target.leg().type() != TransportMode.BUS && target.leg().type() != TransportMode.SUBWAY) {
			throw new RouteException(RouteErrorCode.NOT_TRANSIT_LEG);
		}
		if (target.leg().type() == TransportMode.BUS) {
			response = refreshBus(target);
		} else {
			response = refreshSubway(target);
		}
		log.info(
			"transit refresh completed routeId={} legSequence={} type={} arrivalStatus={} transitCount={}",
			routeId,
			request.legSequence(),
			response.type(),
			response.arrivalStatus(),
			response.transits().size());
		return response;
	}

	private RefreshTarget refreshTarget(UUID userId, String routeId, int legSequence) {
		RouteSession routeSession = getOwnedRouteSession(userId, routeId);
		return refreshTarget(routeSession, legSequence);
	}

	private RouteSession getOwnedRouteSession(UUID userId, String routeId) {
		return routeSessionRepository.findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(
			userId, routeId, RouteSessionStatus.ACTIVE)
			.orElseGet(() -> {
				if (routeSessionRepository.findFirstByUser_UserIdAndRouteIdOrderByUpdatedAtDesc(userId, routeId)
					.isPresent()) {
					throw new RouteException(RouteErrorCode.ROUTE_SESSION_NOT_FOUND);
				}
				if (routeSessionRepository.findFirstByRouteIdOrderByUpdatedAtDesc(routeId).isPresent()) {
					throw new RouteException(RouteErrorCode.ROUTE_ACCESS_DENIED);
				}
				throw new RouteException(RouteErrorCode.ROUTE_SESSION_NOT_FOUND);
			});
	}

	private RefreshTarget refreshTarget(RouteSession routeSession, int legSequence) {
		RouteSummaryResponse route = restoreRouteSnapshot(routeSession);
		RouteLegResponse leg = route.legs()
			.stream()
			.filter(candidate -> candidate.sequence() == legSequence)
			.findFirst()
			.orElseThrow(() -> new RouteException(RouteErrorCode.ROUTE_SESSION_NOT_FOUND));
		return new RefreshTarget(routeSession.getRouteSnapshotJson(), route, leg,
			metadataLeg(route, leg, routeSession));
	}

	private RouteSummaryResponse restoreRouteSnapshot(RouteSession routeSession) {
		if (routeSession.getRouteSnapshotJson() == null || routeSession.getRouteSnapshotJson().isNull()) {
			throw new RouteException(RouteErrorCode.ROUTE_SESSION_NOT_FOUND);
		}
		try {
			RouteSummaryResponse route = objectMapper.treeToValue(
				routePayloadSnapshot(routeSession.getRouteSnapshotJson()),
				RouteSummaryResponse.class);
			if (route == null || route.legs() == null || route.legs().isEmpty()) {
				throw new RouteException(RouteErrorCode.ROUTE_SESSION_NOT_FOUND);
			}
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

	private JsonNode metadataLeg(RouteSummaryResponse route, RouteLegResponse leg, RouteSession routeSession) {
		JsonNode snapshot = routeSession.getRouteSnapshotJson();
		JsonNode metadata = snapshot == null ? null : snapshot.get("backendMetadata");
		JsonNode legs = metadata == null ? null : metadata.get("legs");
		if (legs == null || !legs.isArray()) {
			return null;
		}
		for (JsonNode candidate : legs) {
			if (candidate.path("legSequence").asInt(-1) == leg.sequence()) {
				return candidate;
			}
		}
		int targetOrdinal = transitOrdinal(route, leg);
		int ordinal = 0;
		for (JsonNode candidate : legs) {
			if (sameTransportMode(candidate, leg.type())) {
				ordinal++;
				if (ordinal == targetOrdinal) {
					return candidate;
				}
			}
		}
		return null;
	}

	private int transitOrdinal(RouteSummaryResponse route, RouteLegResponse targetLeg) {
		int ordinal = 0;
		for (RouteLegResponse leg : route.legs()) {
			if (leg.type() == targetLeg.type()) {
				ordinal++;
			}
			if (leg.sequence() == targetLeg.sequence()) {
				return ordinal;
			}
		}
		return ordinal;
	}

	private boolean sameTransportMode(JsonNode node, TransportMode transportMode) {
		return transportMode.name().equals(node.path("type").asText());
	}

	private TransitRefreshResponse refreshBus(RefreshTarget target) {
		String stopId = boardingStopId(target.metadataLeg());
		List<BusLane> lanes = busLanes(target);
		if (!StringUtils.hasText(stopId) || lanes.isEmpty()) {
			log.info(
				"transit refresh bus metadata unavailable routeId={} legSequence={} hasStopId={} laneCount={}",
				target.route().routeId(),
				target.leg().sequence(),
				StringUtils.hasText(stopId),
				lanes.size());
			return new TransitRefreshResponse(TransportMode.BUS, TransitArrivalStatus.ARRIVAL_UNKNOWN, List.of());
		}
		List<TransitArrivalResponse> transits = lanes.stream()
			.map(lane -> busArrival(stopId, lane))
			.filter(result -> result.arrival().remainingMinute() != null)
			.map(result -> new TransitArrivalResponse(
				result.arrival().routeNo(),
				result.arrival().remainingMinute(),
				result.arrival().isLowFloor()))
			.toList();
		if (transits.isEmpty()) {
			return new TransitRefreshResponse(TransportMode.BUS, TransitArrivalStatus.NO_CURRENT_ARRIVAL, List.of());
		}
		return new TransitRefreshResponse(TransportMode.BUS, TransitArrivalStatus.REALTIME_AVAILABLE, transits);
	}

	private String boardingStopId(JsonNode metadataLeg) {
		JsonNode passStops = metadataLeg == null ? null : metadataLeg.get("passStops");
		if (passStops != null && passStops.isArray() && !passStops.isEmpty()) {
			String stopId = text(passStops.get(0), "localStationID");
			if (StringUtils.hasText(stopId)) {
				return stopId;
			}
		}
		return text(metadataLeg, "startLocalStationId");
	}

	private List<BusLane> busLanes(RefreshTarget target) {
		JsonNode lanes = target.metadataLeg() == null ? null : target.metadataLeg().get("lanes");
		if (lanes != null && lanes.isArray() && !lanes.isEmpty()) {
			return StreamSupport.stream(lanes.spliterator(), false)
				.map(lane -> new BusLane(text(lane, "busNo"), text(lane, "busLocalBlID")))
				.filter(lane -> StringUtils.hasText(lane.routeNo()))
				.toList();
		}
		if (target.leg().laneOptions() != null && !target.leg().laneOptions().isEmpty()) {
			return target.leg().laneOptions()
				.stream()
				.map(lane -> new BusLane(lane.routeNo(), null))
				.toList();
		}
		return List.of(new BusLane(target.leg().routeNo(), null)).stream()
			.filter(lane -> StringUtils.hasText(lane.routeNo()))
			.toList();
	}

	private BusArrivalResult busArrival(String stopId, BusLane lane) {
		if (!StringUtils.hasText(lane.lineId())) {
			log.info("bims arrival cache bypass stopId={} routeNo={} reason={}", stopId, lane.routeNo(),
				"missingLineId");
			return new BusArrivalResult(busanBimsClient.findArrival(stopId, null, lane.routeNo()));
		}
		return findCachedBusArrival(stopId, lane)
			.map(arrival -> {
				log.info("bims arrival cache hit stopId={} lineId={} routeNo={}", stopId, lane.lineId(),
					lane.routeNo());
				return new BusArrivalResult(arrival);
			})
			.orElseGet(() -> {
				log.info("bims arrival cache miss stopId={} lineId={} routeNo={}", stopId, lane.lineId(),
					lane.routeNo());
				BusanBimsArrival arrival = busanBimsClient.findArrival(stopId, lane.lineId(), lane.routeNo());
				if (StringUtils.hasText(arrival.stopId()) && StringUtils.hasText(arrival.lineId())) {
					saveBusArrivalCache(arrival);
				}
				return new BusArrivalResult(arrival);
			});
	}

	private Optional<BusanBimsArrival> findCachedBusArrival(String stopId, BusLane lane) {
		try {
			return bimsArrivalCacheService.find(stopId, lane.lineId());
		} catch (RuntimeException exception) {
			log.warn("bims arrival cache read failed stopId={} lineId={} routeNo={}",
				stopId,
				lane.lineId(),
				lane.routeNo(),
				exception);
			return Optional.empty();
		}
	}

	private void saveBusArrivalCache(BusanBimsArrival arrival) {
		try {
			bimsArrivalCacheService.save(arrival);
		} catch (RuntimeException exception) {
			log.warn("bims arrival cache save failed stopId={} lineId={} routeNo={}",
				arrival.stopId(),
				arrival.lineId(),
				arrival.routeNo(),
				exception);
		}
	}

	private String text(JsonNode node, String fieldName) {
		if (node == null || !node.hasNonNull(fieldName)) {
			return null;
		}
		String value = node.get(fieldName).asText();
		return StringUtils.hasText(value) ? value : null;
	}

	private TransitRefreshResponse refreshSubway(RefreshTarget target) {
		String odsayStationId = text(target.metadataLeg(), "odsayStationId");
		Integer wayCode = integer(target.metadataLeg(), "wayCode");
		if (!StringUtils.hasText(odsayStationId) || wayCode == null) {
			log.info(
				"transit refresh subway metadata unavailable routeId={} legSequence={} hasOdsayStationId={} hasWayCode={}",
				target.route().routeId(),
				target.leg().sequence(),
				StringUtils.hasText(odsayStationId),
				wayCode != null);
			return new TransitRefreshResponse(TransportMode.SUBWAY, TransitArrivalStatus.ARRIVAL_UNKNOWN, List.of());
		}
		LocalDateTime now = LocalDateTime.now(clock);
		int secondOfDay = now.toLocalTime().toSecondOfDay();
		SubwayServiceDayType serviceDayType = serviceDayType(now.getDayOfWeek());
		List<SubwayTimetable> departures = subwayTimetableRepository.findNextDepartures(
			odsayStationId,
			serviceDayType,
			wayCode,
			secondOfDay,
			PageRequest.of(0, 2));
		boolean nextDay = false;
		if (departures.isEmpty()) {
			SubwayServiceDayType nextServiceDayType = serviceDayType(now.toLocalDate().plusDays(1).getDayOfWeek());
			log.info(
				"subway timetable same-day departure missing odsayStationId={} wayCode={} serviceDayType={} nextServiceDayType={}",
				odsayStationId,
				wayCode,
				serviceDayType,
				nextServiceDayType);
			departures = subwayTimetableRepository.findFirstDepartures(
				odsayStationId,
				nextServiceDayType,
				wayCode,
				PageRequest.of(0, 2));
			nextDay = true;
		}
		if (departures.isEmpty()) {
			log.info(
				"subway timetable unavailable odsayStationId={} wayCode={} serviceDayType={} nextDayLookup={}",
				odsayStationId,
				wayCode,
				serviceDayType,
				nextDay);
			return new TransitRefreshResponse(TransportMode.SUBWAY, TransitArrivalStatus.ARRIVAL_UNKNOWN, List.of());
		}
		boolean isNextDay = nextDay;
		String routeNo = subwayRouteNo(target);
		List<TransitArrivalResponse> transits = departures.stream()
			.map(departure -> new TransitArrivalResponse(
				routeNo,
				remainingMinute(secondOfDay, departure.getDepartureSecondOfDay(), isNextDay),
				null))
			.toList();
		return new TransitRefreshResponse(TransportMode.SUBWAY, TransitArrivalStatus.SCHEDULE_BASED, transits);
	}

	private String subwayRouteNo(RefreshTarget target) {
		String lineName = text(target.metadataLeg(), "lineName");
		if (StringUtils.hasText(lineName)) {
			return lineName;
		}
		return target.leg().routeNo();
	}

	private Integer integer(JsonNode node, String fieldName) {
		if (node == null || !node.hasNonNull(fieldName)) {
			return null;
		}
		if (node.get(fieldName).canConvertToInt()) {
			return node.get(fieldName).asInt();
		}
		try {
			return Integer.parseInt(node.get(fieldName).asText());
		} catch (NumberFormatException exception) {
			return null;
		}
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

	private record RefreshTarget(
		JsonNode snapshot,
		RouteSummaryResponse route,
		RouteLegResponse leg,
		JsonNode metadataLeg) {
	}

	private record BusLane(
		String routeNo,
		String lineId) {
		BusLane {
			routeNo = Objects.requireNonNullElse(routeNo, "");
		}
	}

	private record BusArrivalResult(
		BusanBimsArrival arrival) {
	}
}
