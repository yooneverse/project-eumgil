package com.ssafy.e102.domain.route.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.ssafy.e102.domain.route.dto.response.BusStopSyncResponse;
import com.ssafy.e102.domain.route.entity.BusStop;
import com.ssafy.e102.domain.route.repository.BusStopRepository;
import com.ssafy.e102.global.external.bims.BusanBimsBusStop;
import com.ssafy.e102.global.external.bims.BusanBimsBusStopPage;
import com.ssafy.e102.global.external.bims.BusanBimsClient;
import com.ssafy.e102.global.geo.GeoDistanceCalculator;
import com.ssafy.e102.global.geo.GeoPointConverter;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class BusStopMasterService {

	private static final int BIMS_BUS_STOP_PAGE_SIZE = 1000;
	private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

	private final BusStopRepository busStopRepository;
	private final BusanBimsClient busanBimsClient;
	private final GeoPointConverter geoPointConverter;
	private final Clock clock = Clock.system(SEOUL_ZONE_ID);
	private volatile List<CachedBusStop> cachedBusStops = List.of();

	public BusStopMasterService(
		BusStopRepository busStopRepository,
		BusanBimsClient busanBimsClient,
		GeoPointConverter geoPointConverter) {
		this.busStopRepository = busStopRepository;
		this.busanBimsClient = busanBimsClient;
		this.geoPointConverter = geoPointConverter;
	}

	@Transactional(readOnly = true)
	public void reloadCache() {
		cachedBusStops = loadActiveBusStops();
	}

	@PostConstruct
	void warmUpCache() {
		reloadCache();
	}

	public Optional<BusStopMatch> findNearest(double lat, double lng, double maxDistanceMeter) {
		List<CachedBusStop> busStops = cachedBusStops;
		if (busStops.isEmpty()) {
			busStops = loadActiveBusStops();
			cachedBusStops = busStops;
		}
		return busStops.stream()
			.map(busStop -> busStop.match(lat, lng))
			.filter(match -> match.distanceMeter() <= maxDistanceMeter)
			.min(Comparator.comparingDouble(BusStopMatch::distanceMeter));
	}

	@Transactional
	public BusStopSyncResponse syncFromBims() {
		List<BusanBimsBusStop> fetchedBusStops = fetchAllBusStops();
		Map<String, BusStop> existingBusStops = busStopsByStopId(fetchedBusStops);
		LocalDateTime syncedAt = LocalDateTime.now(clock);
		List<BusStop> busStopsToSave = new ArrayList<>();
		Set<String> fetchedStopIds = new HashSet<>();

		for (BusanBimsBusStop source : fetchedBusStops) {
			if (!isSyncable(source)) {
				continue;
			}
			fetchedStopIds.add(source.stopId());
			BusStop busStop = existingBusStops.get(source.stopId());
			if (busStop == null) {
				busStop = BusStop.create(
					source.stopId(),
					source.stopName().trim(),
					normalizeNullable(source.arsNo()),
					normalizeNullable(source.stopType()),
					geoPointConverter.toPoint(new GeoPointRequest(source.lat(), source.lng())),
					syncedAt);
			} else {
				busStop.update(
					source.stopName().trim(),
					normalizeNullable(source.arsNo()),
					normalizeNullable(source.stopType()),
					geoPointConverter.toPoint(new GeoPointRequest(source.lat(), source.lng())),
					syncedAt);
			}
			busStopsToSave.add(busStop);
		}

		List<BusStop> activeBusStops = busStopRepository.findAllByActiveTrue();
		int deactivatedCount = 0;
		for (BusStop busStop : activeBusStops) {
			if (!fetchedStopIds.contains(busStop.getBstopId())) {
				busStop.deactivate(syncedAt);
				busStopsToSave.add(busStop);
				deactivatedCount++;
			}
		}

		busStopRepository.saveAll(busStopsToSave);
		reloadCache();
		return new BusStopSyncResponse(
			fetchedBusStops.size(),
			busStopsToSave.size(),
			deactivatedCount,
			cachedBusStops.size());
	}

	private List<CachedBusStop> loadActiveBusStops() {
		try {
			return busStopRepository.findAllByActiveTrue()
				.stream()
				.map(CachedBusStop::from)
				.toList();
		} catch (DataAccessException exception) {
			log.warn("버스정류장 마스터를 조회할 수 없어 정류장명 보강을 생략합니다.", exception);
			return List.of();
		}
	}

	private List<BusanBimsBusStop> fetchAllBusStops() {
		List<BusanBimsBusStop> busStops = new ArrayList<>();
		int pageNo = 1;
		int totalCount = Integer.MAX_VALUE;
		while (busStops.size() < totalCount) {
			BusanBimsBusStopPage page = busanBimsClient.findBusStops(pageNo, BIMS_BUS_STOP_PAGE_SIZE);
			totalCount = page.totalCount();
			if (page.busStops().isEmpty()) {
				break;
			}
			busStops.addAll(page.busStops());
			pageNo++;
		}
		return List.copyOf(busStops);
	}

	private Map<String, BusStop> busStopsByStopId(Collection<BusanBimsBusStop> sourceBusStops) {
		List<String> stopIds = sourceBusStops.stream()
			.map(BusanBimsBusStop::stopId)
			.filter(StringUtils::hasText)
			.distinct()
			.toList();
		if (stopIds.isEmpty()) {
			return Map.of();
		}
		Map<String, BusStop> busStopsByStopId = new HashMap<>();
		for (BusStop busStop : busStopRepository.findAllByBstopIdIn(stopIds)) {
			busStopsByStopId.put(busStop.getBstopId(), busStop);
		}
		return busStopsByStopId;
	}

	private boolean isSyncable(BusanBimsBusStop source) {
		return StringUtils.hasText(source.stopId())
			&& StringUtils.hasText(source.stopName())
			&& source.lat() != null
			&& source.lng() != null;
	}

	private String normalizeNullable(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	private record CachedBusStop(
		String stopId,
		String stopName,
		String arsNo,
		String stopType,
		double lat,
		double lng) {

		static CachedBusStop from(BusStop busStop) {
			return new CachedBusStop(
				busStop.getBstopId(),
				busStop.getStopName(),
				busStop.getArsNo(),
				busStop.getStopType(),
				busStop.getPoint().getY(),
				busStop.getPoint().getX());
		}

		BusStopMatch match(double targetLat, double targetLng) {
			return new BusStopMatch(
				stopId,
				stopName,
				arsNo,
				stopType,
				GeoDistanceCalculator.distanceMeter(targetLat, targetLng, lat, lng));
		}
	}

	public record BusStopMatch(
		String stopId,
		String stopName,
		String arsNo,
		String stopType,
		double distanceMeter) {
	}
}
