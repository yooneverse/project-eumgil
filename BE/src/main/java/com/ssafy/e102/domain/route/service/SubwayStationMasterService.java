package com.ssafy.e102.domain.route.service;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.ssafy.e102.domain.place.type.AccessibilityFeatureType;
import com.ssafy.e102.domain.route.entity.SubwayStation;
import com.ssafy.e102.domain.route.entity.SubwayStationAccessibilityFeature;
import com.ssafy.e102.domain.route.entity.SubwayStationElevator;
import com.ssafy.e102.domain.route.repository.SubwayStationAccessibilityFeatureRepository;
import com.ssafy.e102.domain.route.repository.SubwayStationElevatorRepository;
import com.ssafy.e102.domain.route.repository.SubwayStationRepository;
import com.ssafy.e102.global.geo.GeoDistanceCalculator;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SubwayStationMasterService {

	private final SubwayStationRepository subwayStationRepository;
	private final SubwayStationAccessibilityFeatureRepository accessibilityFeatureRepository;
	private final SubwayStationElevatorRepository subwayStationElevatorRepository;
	private volatile List<SubwayStation> cachedStations;

	public SubwayStationMasterService(
		SubwayStationRepository subwayStationRepository,
		SubwayStationAccessibilityFeatureRepository accessibilityFeatureRepository,
		SubwayStationElevatorRepository subwayStationElevatorRepository) {
		this.subwayStationRepository = subwayStationRepository;
		this.accessibilityFeatureRepository = accessibilityFeatureRepository;
		this.subwayStationElevatorRepository = subwayStationElevatorRepository;
	}

	public Optional<SubwayStationPlaceDetail> findPlaceDetail(
		String nameHint,
		double lat,
		double lng,
		double maxDistanceMeter) {
		String normalizedKeyword = normalizeStationKeyword(nameHint);
		if (!StringUtils.hasText(normalizedKeyword)) {
			return Optional.empty();
		}
		List<SubwayStation> stations = loadStations();
		return stations.stream()
			.filter(station -> station.getPoint() != null)
			.filter(station -> normalizedKeyword.equals(normalizeStationKeyword(station.getStationName())))
			.map(station -> new SubwayStationMatch(
				station,
				GeoDistanceCalculator.distanceMeter(lat, lng, station.getPoint().getY(), station.getPoint().getX())))
			.filter(match -> match.distanceMeter() <= maxDistanceMeter)
			.min(Comparator.comparingDouble(SubwayStationMatch::distanceMeter))
			.map(match -> toPlaceDetail(match.station(), stations));
	}

	public Optional<SubwayStationPlaceDetail> findNearestPlaceDetail(
		double lat,
		double lng,
		double maxDistanceMeter) {
		List<SubwayStation> stations = loadStations();
		return stations.stream()
			.filter(station -> station.getPoint() != null)
			.map(station -> new SubwayStationMatch(
				station,
				GeoDistanceCalculator.distanceMeter(lat, lng, station.getPoint().getY(), station.getPoint().getX())))
			.filter(match -> match.distanceMeter() <= maxDistanceMeter)
			.min(Comparator.comparingDouble(SubwayStationMatch::distanceMeter))
			.map(match -> toPlaceDetail(match.station(), stations));
	}

	private List<SubwayStation> loadStations() {
		List<SubwayStation> stations = cachedStations;
		if (stations != null) {
			return stations;
		}
		try {
			stations = List.copyOf(subwayStationRepository.findAll());
			cachedStations = stations;
			return stations;
		} catch (DataAccessException exception) {
			log.warn("지하철역 마스터를 조회할 수 없어 지하철역 POI 매칭을 생략합니다.", exception);
			return List.of();
		}
	}

	private SubwayStationPlaceDetail toPlaceDetail(SubwayStation matchedStation, List<SubwayStation> stations) {
		List<SubwayStation> stationGroup = stationGroup(matchedStation, stations);
		return new SubwayStationPlaceDetail(
			matchedStation,
			stationGroup,
			groupProviderPlaceId(stationGroup),
			lineNames(stationGroup),
			resolveAccessibilityFeatures(stationGroup));
	}

	private List<SubwayStation> stationGroup(SubwayStation matchedStation, List<SubwayStation> stations) {
		String normalizedStationName = normalizeStationKeyword(matchedStation.getStationName());
		return stations.stream()
			.filter(station -> normalizedStationName.equals(normalizeStationKeyword(station.getStationName())))
			.sorted(Comparator.comparing(SubwayStation::getOdsayStationId))
			.toList();
	}

	private String groupProviderPlaceId(List<SubwayStation> stationGroup) {
		return "GROUP:" + String.join("-", stationGroup.stream()
			.map(SubwayStation::getOdsayStationId)
			.sorted()
			.toList());
	}

	private List<String> lineNames(List<SubwayStation> stationGroup) {
		return stationGroup.stream()
			.map(SubwayStation::getLineName)
			.filter(StringUtils::hasText)
			.distinct()
			.sorted()
			.toList();
	}

	private List<SubwayAccessibilityFeature> resolveAccessibilityFeatures(List<SubwayStation> stationGroup) {
		Map<AccessibilityFeatureType, Boolean> availabilityByType = new EnumMap<>(AccessibilityFeatureType.class);
		for (SubwayStation station : stationGroup) {
			for (SubwayStationAccessibilityFeature feature : loadAccessibilityFeatures(station)) {
				availabilityByType.merge(feature.getFeatureType(), feature.isAvailable(), Boolean::logicalOr);
			}
			if (!loadElevators(station.getOdsayStationId()).isEmpty()) {
				availabilityByType.merge(AccessibilityFeatureType.elevator, true, Boolean::logicalOr);
			}
		}
		return availabilityByType.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByKey(Comparator.comparingInt(Enum::ordinal)))
			.map(entry -> new SubwayAccessibilityFeature(entry.getKey(), entry.getValue()))
			.toList();
	}

	private List<SubwayStationAccessibilityFeature> loadAccessibilityFeatures(SubwayStation station) {
		try {
			return accessibilityFeatureRepository.findAllBySubwayStation(station);
		} catch (DataAccessException exception) {
			log.warn("지하철역 접근성 속성을 조회할 수 없어 접근성 응답을 일부 생략합니다. odsayStationId={}",
				station.getOdsayStationId(),
				exception);
			return List.of();
		}
	}

	private List<SubwayStationElevator> loadElevators(String odsayStationId) {
		try {
			return subwayStationElevatorRepository.findByOdsayStationId(odsayStationId);
		} catch (DataAccessException exception) {
			log.warn("지하철역 엘리베이터 정보를 조회할 수 없어 elevator 접근성 보강을 생략합니다. odsayStationId={}",
				odsayStationId,
				exception);
			return List.of();
		}
	}

	private String normalizeStationKeyword(String value) {
		if (!StringUtils.hasText(value)) {
			return "";
		}
		String normalized = value.trim();
		int stationNameEndIndex = normalized.lastIndexOf("역");
		if (stationNameEndIndex >= 0) {
			normalized = normalized.substring(0, stationNameEndIndex);
		}
		return normalized
			.replace("지하철", "")
			.replaceAll("\\([^)]*\\)", "")
			.replace("·", "")
			.replace(".", "")
			.replaceAll("\\s+", "")
			.trim();
	}

	private record SubwayStationMatch(
		SubwayStation station,
		double distanceMeter) {
	}

	public record SubwayStationPlaceDetail(
		SubwayStation station,
		List<SubwayStation> stationGroup,
		String groupProviderPlaceId,
		List<String> lineNames,
		List<SubwayAccessibilityFeature> accessibilityFeatures) {
	}

	public record SubwayAccessibilityFeature(
		AccessibilityFeatureType featureType,
		boolean isAvailable) {
	}
}
