package com.ssafy.e102.domain.route.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.ssafy.e102.domain.place.type.AccessibilityFeatureType;
import com.ssafy.e102.domain.route.entity.SubwayStation;
import com.ssafy.e102.domain.route.entity.SubwayStationAccessibilityFeature;
import com.ssafy.e102.domain.route.entity.SubwayStationElevator;
import com.ssafy.e102.domain.route.repository.SubwayStationAccessibilityFeatureRepository;
import com.ssafy.e102.domain.route.repository.SubwayStationElevatorRepository;
import com.ssafy.e102.domain.route.repository.SubwayStationRepository;
import com.ssafy.e102.global.geo.GeoPointConverter;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

class SubwayStationMasterServiceTest {

	@Mock
	private SubwayStationRepository subwayStationRepository;

	@Mock
	private SubwayStationAccessibilityFeatureRepository accessibilityFeatureRepository;

	@Mock
	private SubwayStationElevatorRepository subwayStationElevatorRepository;

	private SubwayStationMasterService subwayStationMasterService;
	private GeoPointConverter geoPointConverter;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		geoPointConverter = new GeoPointConverter();
		subwayStationMasterService = new SubwayStationMasterService(
			subwayStationRepository,
			accessibilityFeatureRepository,
			subwayStationElevatorRepository);
	}

	@Test
	@DisplayName("지하철역 이름 힌트와 좌표 기준으로 허용 반경 안의 역을 매칭한다")
	void findPlaceDetail() {
		SubwayStation far = station("70901", "사상", "부산-김해경전철", 35.1630, 128.9868);
		SubwayStation near = station("70227", "사상", "부산 2호선", 35.162166, 128.984611);
		when(subwayStationRepository.findAll()).thenReturn(List.of(far, near));
		when(accessibilityFeatureRepository.findAllBySubwayStation(far))
			.thenReturn(List.of(SubwayStationAccessibilityFeature.create(
				far,
				AccessibilityFeatureType.guidanceFacility,
				true)));
		when(accessibilityFeatureRepository.findAllBySubwayStation(near))
			.thenReturn(List.of(SubwayStationAccessibilityFeature.create(
				near,
				AccessibilityFeatureType.accessibleToilet,
				true)));
		when(subwayStationElevatorRepository.findByOdsayStationId("70901"))
			.thenReturn(List.of(elevator("70901", "사상", "부산-김해경전철", 35.1630, 128.9868)));
		when(subwayStationElevatorRepository.findByOdsayStationId("70227")).thenReturn(List.of());

		Optional<SubwayStationMasterService.SubwayStationPlaceDetail> result = subwayStationMasterService
			.findPlaceDetail("사상역 1번출구", 35.162166, 128.984611, 300.0);

		assertThat(result).isPresent();
		assertThat(result.get().station()).isSameAs(near);
		assertThat(result.get().groupProviderPlaceId()).isEqualTo("GROUP:70227-70901");
		assertThat(result.get().lineNames()).containsExactly("부산 2호선", "부산-김해경전철");
		assertThat(result.get().accessibilityFeatures())
			.extracting(SubwayStationMasterService.SubwayAccessibilityFeature::featureType)
			.containsExactly(
				AccessibilityFeatureType.elevator,
				AccessibilityFeatureType.accessibleToilet,
				AccessibilityFeatureType.guidanceFacility);
	}

	@Test
	@DisplayName("지하철역 이름이 다르거나 허용 반경 밖이면 매칭하지 않는다")
	void findPlaceDetailRejectsNameMismatchAndFarStation() {
		SubwayStation station = station("70227", "사상", "부산 2호선", 35.162166, 128.984611);
		when(subwayStationRepository.findAll()).thenReturn(List.of(station));

		Optional<SubwayStationMasterService.SubwayStationPlaceDetail> nameMismatch = subwayStationMasterService
			.findPlaceDetail("서면역", 35.162166, 128.984611, 300.0);
		Optional<SubwayStationMasterService.SubwayStationPlaceDetail> tooFar = subwayStationMasterService
			.findPlaceDetail("사상역", 35.1800, 129.0100, 300.0);

		assertThat(nameMismatch).isEmpty();
		assertThat(tooFar).isEmpty();
	}

	private SubwayStation station(String odsayStationId, String stationName, String lineName, double lat, double lng) {
		return SubwayStation.create(
			odsayStationId,
			stationName,
			lineName,
			geoPointConverter.toPoint(new GeoPointRequest(lat, lng)));
	}

	private SubwayStationElevator elevator(String odsayStationId, String stationName, String lineName, double lat,
		double lng) {
		return SubwayStationElevator.create(
			odsayStationId,
			stationName,
			lineName,
			geoPointConverter.toPoint(new GeoPointRequest(lat, lng)));
	}
}
