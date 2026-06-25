package com.ssafy.e102.domain.route.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.ssafy.e102.domain.route.dto.response.BusStopSyncResponse;
import com.ssafy.e102.domain.route.entity.BusStop;
import com.ssafy.e102.domain.route.repository.BusStopRepository;
import com.ssafy.e102.global.external.bims.BusanBimsBusStop;
import com.ssafy.e102.global.external.bims.BusanBimsBusStopPage;
import com.ssafy.e102.global.external.bims.BusanBimsClient;
import com.ssafy.e102.global.geo.GeoPointConverter;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

class BusStopMasterServiceTest {

	@Mock
	private BusStopRepository busStopRepository;

	@Mock
	private BusanBimsClient busanBimsClient;

	private BusStopMasterService busStopMasterService;
	private GeoPointConverter geoPointConverter;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		geoPointConverter = new GeoPointConverter();
		busStopMasterService = new BusStopMasterService(
			busStopRepository,
			busanBimsClient,
			geoPointConverter);
	}

	@Test
	@DisplayName("클릭 좌표 기준 허용 반경 안의 가장 가까운 버스정류장을 찾는다")
	void findNearest() {
		BusStop far = busStop("1", "먼정류장", "10001", 35.0630, 128.9800);
		BusStop near = busStop("2", "다대현대아파트", "10175", 35.062268914248, 128.977167399072);
		when(busStopRepository.findAllByActiveTrue()).thenReturn(List.of(far, near));

		Optional<BusStopMasterService.BusStopMatch> match = busStopMasterService.findNearest(
			35.06225,
			128.97716,
			150.0);

		assertThat(match).isPresent();
		assertThat(match.get().stopId()).isEqualTo("2");
		assertThat(match.get().stopName()).isEqualTo("다대현대아파트");
		assertThat(match.get().arsNo()).isEqualTo("10175");
	}

	@Test
	@DisplayName("애플리케이션 시작 시 버스정류장 마스터 캐시를 미리 적재한다")
	void warmUpCache() {
		when(busStopRepository.findAllByActiveTrue())
			.thenReturn(List.of(busStop("1", "시작정류장", "10001", 35.0, 128.0)));

		busStopMasterService.warmUpCache();
		Optional<BusStopMasterService.BusStopMatch> match = busStopMasterService.findNearest(35.0, 128.0, 30.0);

		assertThat(match).isPresent();
		assertThat(match.get().stopName()).isEqualTo("시작정류장");
		Mockito.verify(busStopRepository, times(1)).findAllByActiveTrue();
	}

	@Test
	@DisplayName("BIMS busStopList 전체 페이지를 DB 마스터에 반영하고 사라진 활성 정류장을 비활성화한다")
	@SuppressWarnings("unchecked")
	void syncFromBims() {
		BusStop existing = busStop("1", "기존정류장", "10001", 35.0, 128.0);
		BusStop stale = busStop("stale", "삭제된정류장", "19999", 35.1, 128.1);
		when(busanBimsClient.findBusStops(1, 1000))
			.thenReturn(new BusanBimsBusStopPage(List.of(
				new BusanBimsBusStop("1", "수정정류장", "10002", 128.2, 35.2, "일반")), 2, 1, 1000));
		when(busanBimsClient.findBusStops(2, 1000))
			.thenReturn(new BusanBimsBusStopPage(List.of(
				new BusanBimsBusStop("2", "신규정류장", "10003", 128.3, 35.3, "일반")), 2, 2, 1000));
		when(busStopRepository.findAllByBstopIdIn(anyCollection())).thenReturn(List.of(existing));
		when(busStopRepository.findAllByActiveTrue())
			.thenReturn(List.of(existing, stale))
			.thenReturn(List.of(existing, stale));

		BusStopSyncResponse response = busStopMasterService.syncFromBims();

		ArgumentCaptor<List<BusStop>> captor = ArgumentCaptor.forClass(List.class);
		Mockito.verify(busStopRepository).saveAll(captor.capture());
		assertThat(response.fetchedCount()).isEqualTo(2);
		assertThat(response.deactivatedCount()).isEqualTo(1);
		assertThat(captor.getValue()).hasSize(3);
		assertThat(existing.getStopName()).isEqualTo("수정정류장");
		assertThat(existing.getArsNo()).isEqualTo("10002");
		assertThat(stale.isActive()).isFalse();
	}

	private BusStop busStop(String stopId, String stopName, String arsNo, double lat, double lng) {
		return BusStop.create(
			stopId,
			stopName,
			arsNo,
			"일반",
			geoPointConverter.toPoint(new GeoPointRequest(lat, lng)),
			java.time.LocalDateTime.now());
	}
}
