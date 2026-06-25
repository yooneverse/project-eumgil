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

import com.ssafy.e102.domain.admin.repository.AdminBottleneckMonitoringQueryRepository;
import com.ssafy.e102.domain.admin.repository.AdminBottleneckMonitoringQueryRepository.BottleneckCandidateRow;
import com.ssafy.e102.domain.admin.repository.AdminBottleneckMonitoringQueryRepository.DailyTrendRow;
import com.ssafy.e102.domain.place.repository.PlaceRepository;

@ExtendWith(MockitoExtension.class)
class AdminBottleneckMonitoringServiceTest {

	@Mock
	private AdminBottleneckMonitoringQueryRepository queryRepository;

	@Mock
	private PlaceRepository placeRepository;

	@Test
	@DisplayName("병목구간 통계는 실데이터 후보를 KPI, 지도, 목록 계약으로 변환한다")
	void getBottleneckMonitoring() {
		LocalDate from = LocalDate.of(2026, 5, 11);
		LocalDate to = LocalDate.of(2026, 5, 17);
		LocalDateTime start = from.atStartOfDay();
		LocalDateTime endExclusive = to.plusDays(1).atStartOfDay();
		LocalDate previousFrom = from.minusDays(7);
		LocalDate previousTo = from.minusDays(1);
		LocalDateTime previousStart = previousFrom.atStartOfDay();
		LocalDateTime previousEndExclusive = previousTo.plusDays(1).atStartOfDay();

		when(queryRepository.findCandidateRows(start, endExclusive)).thenReturn(List.of(
			new BottleneckCandidateRow(
				"LINESTRING(128.872857 35.081392, 128.903468 35.094187)",
				"안전 경로",
				17,
				9,
				0.74,
				3,
				3,
				0,
				0,
				LocalDateTime.of(2026, 5, 16, 9, 30),
				true,
				false,
				false,
				4,
				1,
				0,
				0,
				"강서구",
				"신호동",
				"강서구",
				"명지1동",
				"부산광역시 강서구 신호동"),
			new BottleneckCandidateRow(
				"LINESTRING(128.875000 35.090000, 128.880000 35.095000)",
				"추천 경로",
				11,
				5,
				1.21,
				1,
				1,
				0,
				0,
				LocalDateTime.of(2026, 5, 15, 12, 0),
				false,
				true,
				false,
				0,
				3,
				1,
				0,
				"강서구",
				"녹산동",
				"강서구",
				"신호동",
				"부산광역시 강서구 녹산동"),
			new BottleneckCandidateRow(
				"LINESTRING(128.881000 35.098000, 128.882000 35.099000)",
				"안전 경로",
				4,
				2,
				1.52,
				0,
				0,
				0,
				0,
				null,
				false,
				false,
				false,
				0,
				0,
				2,
				0,
				"강서구",
				"명지1동",
				"강서구",
				"신호동",
				null),
			new BottleneckCandidateRow(
				"LINESTRING(128.883000 35.100000, 128.884000 35.101000)",
				"추천 경로",
				3,
				1,
				1.83,
				1,
				0,
				1,
				0,
				LocalDateTime.of(2026, 5, 14, 8, 0),
				false,
				false,
				true,
				0,
				0,
				0,
				3,
				"강서구",
				"명지1동",
				"강서구",
				"명지1동",
				"부산광역시 강서구 명지동")));
		when(queryRepository.findCandidateRows(previousStart, previousEndExclusive)).thenReturn(List.of(
			new BottleneckCandidateRow(
				"LINESTRING(128.872857 35.081392, 128.903468 35.094187)",
				"안전 경로",
				10,
				4,
				0.82,
				1,
				1,
				0,
				0,
				LocalDateTime.of(2026, 5, 9, 11, 0),
				true,
				false,
				false,
				2,
				0,
				0,
				0,
				"강서구",
				"신호동",
				"강서구",
				"명지1동",
				"부산광역시 강서구 신호동"),
			new BottleneckCandidateRow(
				"LINESTRING(128.875000 35.090000, 128.880000 35.095000)",
				"추천 경로",
				5,
				2,
				1.45,
				0,
				0,
				0,
				0,
				null,
				false,
				false,
				false,
				0,
				1,
				0,
				0,
				"강서구",
				"녹산동",
				"강서구",
				"신호동",
				null)));
		when(placeRepository.findNearestPlaceName(anyDouble(), anyDouble(), eq(120)))
			.thenReturn(Optional.of("명지오션시티"));
		when(queryRepository.findDailyTrendRows(start, endExclusive)).thenReturn(List.of(
			new DailyTrendRow(LocalDate.of(2026, 5, 11), 3, 2),
			new DailyTrendRow(LocalDate.of(2026, 5, 14), 4, 3),
			new DailyTrendRow(LocalDate.of(2026, 5, 17), 5, 2)));

		AdminBottleneckMonitoringService service = new AdminBottleneckMonitoringService(queryRepository, placeRepository);

		var response = service.getMonitoring(from, to);

		assertThat(response.summaryCards()).hasSize(4);
		assertThat(response.summaryCards().get(0).label()).isEqualTo("병목구간 총수");
		assertThat(response.summaryCards().get(0).valueLabel()).isEqualTo("4건");
		assertThat(response.summaryCards().get(1).valueLabel()).isEqualTo("3건");
		assertThat(response.summaryCards().get(2).valueLabel()).isEqualTo("15명");
		assertThat(response.summaryCards().get(3).valueLabel()).isEqualTo("1건");
		assertThat(response.distribution().totalCount()).isEqualTo(4);
		assertThat(response.distribution().items()).extracting(item -> item.label())
			.containsExactly("좁은 보행로", "경사/단차", "횡단 주의", "시설물 장애", "기타");
		assertThat(response.map().routeSegments()).hasSize(4);
		assertThat(response.table().rows()).hasSize(4);
		assertThat(response.table().rows().get(0).location()).isEqualTo("명지오션시티 인근");
		assertThat(response.table().rows().get(0).statusLabel()).isEqualTo("심각");
		assertThat(response.impactTop().items()).hasSize(4);
		assertThat(response.impactTop().items().get(0).affectedUsersLabel()).isEqualTo("9명");
		assertThat(response.trend().labels()).containsExactly("05.11", "05.12", "05.13", "05.14", "05.15", "05.16", "05.17");
		assertThat(response.trend().series().get(0).values()).containsExactly(3.0, 0.0, 0.0, 4.0, 0.0, 0.0, 5.0);
	}
}
