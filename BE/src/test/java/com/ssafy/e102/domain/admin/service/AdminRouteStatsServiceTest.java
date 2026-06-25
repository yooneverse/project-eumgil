package com.ssafy.e102.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ssafy.e102.domain.admin.repository.AdminRouteStatsQueryRepository;
import com.ssafy.e102.domain.admin.repository.AdminRouteStatsQueryRepository.AverageDistanceRow;
import com.ssafy.e102.domain.admin.repository.AdminRouteStatsQueryRepository.DistanceBucketRow;
import com.ssafy.e102.domain.admin.repository.AdminRouteStatsQueryRepository.HeatmapRow;
import com.ssafy.e102.domain.admin.repository.AdminRouteStatsQueryRepository.MobilityBreakdownRow;
import com.ssafy.e102.domain.admin.repository.AdminRouteStatsQueryRepository.SpeedTrendRow;
import com.ssafy.e102.domain.admin.repository.AdminRouteStatsQueryRepository.TopRouteRow;
import com.ssafy.e102.domain.place.repository.PlaceRepository;

@ExtendWith(MockitoExtension.class)
class AdminRouteStatsServiceTest {

	@Mock
	private AdminRouteStatsQueryRepository queryRepository;

	@Mock
	private PlaceRepository placeRepository;

	@Test
	@DisplayName("경로/이동 통계는 실데이터 집계 결과를 화면 계약 형태로 변환한다")
	void getRouteStats() {
		LocalDate from = LocalDate.of(2026, 5, 11);
		LocalDate to = LocalDate.of(2026, 5, 17);
		LocalDateTime start = from.atStartOfDay();
		LocalDateTime endExclusive = to.plusDays(1).atStartOfDay();
		when(queryRepository.countTrips(start, endExclusive, "ALL")).thenReturn(12L);
		when(queryRepository.findMobilityBreakdown(start, endExclusive)).thenReturn(List.of(
			new MobilityBreakdownRow("MOBILITY_SUPPORT", 5),
			new MobilityBreakdownRow("POWER_WHEELCHAIR", 2),
			new MobilityBreakdownRow("MANUAL_WHEELCHAIR", 1),
			new MobilityBreakdownRow("VISUAL_IMPAIRMENT", 4)));
		when(queryRepository.findHeatmapRows(start, endExclusive, "ALL")).thenReturn(List.of(
			new HeatmapRow(1, 0, 2),
			new HeatmapRow(1, 1, 4),
			new HeatmapRow(0, 5, 1)));
		when(queryRepository.findSpeedTrendRows(start, endExclusive)).thenReturn(List.of(
			new SpeedTrendRow("MOBILITY_SUPPORT", 0, 4.2),
			new SpeedTrendRow("VISUAL_IMPAIRMENT", 1, 2.8)));
		when(queryRepository.findDistanceBucketRows(start, endExclusive)).thenReturn(List.of(
			new DistanceBucketRow("MOBILITY_SUPPORT", "~1km", 2),
			new DistanceBucketRow("MOBILITY_SUPPORT", "1~3km", 3),
			new DistanceBucketRow("VISUAL_IMPAIRMENT", "~1km", 4)));
		when(queryRepository.findAverageDistanceRows(start, endExclusive)).thenReturn(List.of(
			new AverageDistanceRow("MOBILITY_SUPPORT", 2.4),
			new AverageDistanceRow("VISUAL_IMPAIRMENT", 0.9)));
		when(queryRepository.findOverallAverageDistanceKm(start, endExclusive, "ALL")).thenReturn(3.1);
		when(queryRepository.findTopRouteRows(start, endExclusive, "ALL", 12)).thenReturn(List.of(
			new TopRouteRow(
				"LINESTRING(128.872857 35.081392, 128.903468 35.094187)",
				"안전 경로",
				5835.0,
				1800.0,
				6,
				3.241,
				2,
				"강서구",
				"신호동",
				"강서구",
				"명지1동")));
		when(placeRepository.findNearestPlaceName(anyDouble(), anyDouble(), eq(120)))
			.thenReturn(Optional.of("명지오션시티"));

		AdminRouteStatsService service = new AdminRouteStatsService(queryRepository, placeRepository);

		var response = service.getRouteStats(from, to);

		assertThat(response.summary().totalTrips()).isEqualTo(12);
		assertThat(response.filters().defaults().mobility()).isEqualTo("ALL");
		assertThat(response.typeBreakdown()).hasSize(4);
		assertThat(response.typeBreakdown().get(0).label()).isEqualTo("보행약자");
		assertThat(response.hourlyHeatmap().values().get(0).get(1)).isEqualTo(1.0);
		assertThat(response.hourlyHeatmap().values().get(6).get(5)).isEqualTo(0.25);
		assertThat(response.speedTrend().series().get(0).values().get(0)).isEqualTo(4.2);
		assertThat(response.topRoutes()).hasSize(1);
		assertThat(response.topRoutes().get(0).name()).isEqualTo("명지오션시티 인근");
		assertThat(response.map().routeSegments()).hasSize(1);
		assertThat(response.map().hotspots()).hasSize(1);
		assertThat(response.averageDistance().get(0).label()).isEqualTo("전체");
		assertThat(response.averageDistance().get(0).kilometer()).isEqualTo(3.1);
		assertThat(response.infoItems().get(2).value()).isEqualTo("경로명은 장소명 우선, 부족하면 행정동 기반 대표 이동축으로 표기합니다.");
	}
}
