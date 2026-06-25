package com.ssafy.e102.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ssafy.e102.domain.admin.repository.AdminAreaAssignmentRepository;
import com.ssafy.e102.domain.admin.type.AdminAreaAssignmentType;
import com.ssafy.e102.domain.admin.type.AdminAreaWorkStatus;
import com.ssafy.e102.domain.place.repository.PlaceRepository;
import com.ssafy.e102.domain.report.repository.HazardReportRepository;
import com.ssafy.e102.domain.report.type.ReportStatus;
import com.ssafy.e102.domain.report.type.ReportType;
import com.ssafy.e102.domain.route.repository.RoadSegmentRepository;
import com.ssafy.e102.domain.route.repository.RouteSessionRepository;
import com.ssafy.e102.domain.route.type.RouteSessionStatus;
import com.ssafy.e102.domain.user.repository.UserRepository;
import com.ssafy.e102.domain.user.type.PrimaryUserType;
import com.ssafy.e102.domain.user.type.UserRole;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private RouteSessionRepository routeSessionRepository;

	@Mock
	private HazardReportRepository hazardReportRepository;

	@Mock
	private AdminAreaAssignmentRepository adminAreaAssignmentRepository;

	@Mock
	private RoadSegmentRepository roadSegmentRepository;

	@Mock
	private PlaceRepository placeRepository;

	@Mock
	private AdminAuditLogService adminAuditLogService;

	@Test
	@DisplayName("관리자 홈 요약은 사용자, 길안내, 제보, 데이터 품질 지표를 집계한다")
	void getSummary() {
		LocalDate from = LocalDate.of(2026, 5, 14);
		LocalDate to = LocalDate.of(2026, 5, 14);
		LocalDateTime start = from.atStartOfDay();
		LocalDateTime endExclusive = to.plusDays(1).atStartOfDay();
		LocalDateTime activeStart = to.minusDays(6).atStartOfDay();
		when(userRepository.count()).thenReturn(100L);
		when(userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, endExclusive)).thenReturn(6L);
		when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(2L);
		when(userRepository.countByPrimaryUserType()).thenReturn(List.of(
			new UserTypeCount(PrimaryUserType.LOW_VISION, 40),
			new UserTypeCount(PrimaryUserType.MOBILITY_IMPAIRED, 60)));
		when(routeSessionRepository.count()).thenReturn(80L);
		when(routeSessionRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, endExclusive))
			.thenReturn(12L);
		when(routeSessionRepository.countByStatusAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(
			RouteSessionStatus.COMPLETED, start, endExclusive)).thenReturn(9L);
		when(routeSessionRepository.countByStatus(RouteSessionStatus.ACTIVE)).thenReturn(5L);
		when(routeSessionRepository.averageCompletedDurationMinutes(start, endExclusive)).thenReturn(14.5);
		when(routeSessionRepository.averageRouteSpeedMps(start, endExclusive)).thenReturn(0.82);
		when(routeSessionRepository.findDailyMovementMetrics(start, endExclusive)).thenReturn(List.of(
			new DailyMovementMetric(from, 12L, 7L)));
		when(routeSessionRepository.countDistinctUsersByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
			activeStart, endExclusive)).thenReturn(42L);
		when(hazardReportRepository.count()).thenReturn(30L);
		when(hazardReportRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, endExclusive))
			.thenReturn(4L);
		when(hazardReportRepository.countByStatus(ReportStatus.PENDING)).thenReturn(8L);
		when(hazardReportRepository.countByStatus(ReportStatus.APPROVED)).thenReturn(12L);
		when(hazardReportRepository.countByStatus(ReportStatus.REJECTED)).thenReturn(10L);
		when(hazardReportRepository.countByReportType())
			.thenReturn(List.of(new ReportTypeCount(ReportType.STAIRS_STEP, 5)));
		when(hazardReportRepository.findRecentForDashboard(PageRequest.of(0, 3))).thenReturn(List.of());
		when(roadSegmentRepository.count()).thenReturn(500L);
		when(placeRepository.count()).thenReturn(120L);
		when(adminAreaAssignmentRepository.countByAssignmentType(AdminAreaAssignmentType.ROAD_NETWORK)).thenReturn(10L);
		when(adminAreaAssignmentRepository.countByAssignmentTypeAndStatus(
			AdminAreaAssignmentType.ROAD_NETWORK, AdminAreaWorkStatus.COMPLETED)).thenReturn(7L);
		when(adminAreaAssignmentRepository.countByAssignmentType(AdminAreaAssignmentType.FACILITY)).thenReturn(6L);
		when(adminAreaAssignmentRepository.countByAssignmentTypeAndStatus(
			AdminAreaAssignmentType.FACILITY, AdminAreaWorkStatus.COMPLETED)).thenReturn(3L);
		when(adminAuditLogService.getLogs(null, 5))
			.thenReturn(new com.ssafy.e102.domain.admin.dto.response.AdminAuditLogListResponse(
				List.of(), 5, null, false));

		AdminDashboardService service = new AdminDashboardService(
			userRepository,
			routeSessionRepository,
			hazardReportRepository,
			adminAreaAssignmentRepository,
			roadSegmentRepository,
			placeRepository,
			adminAuditLogService);

		var response = service.getSummary(from, to);

		assertThat(response.users().totalUsers()).isEqualTo(100);
		assertThat(response.users().routeActiveUsers7d()).isEqualTo(42);
		assertThat(response.routes().navigationCompletionRate()).isEqualTo(0.75);
		assertThat(response.routes().averageNavigationMinutes()).isEqualTo(14.5);
		assertThat(response.routes().activeRouteSessions()).isEqualTo(5);
		assertThat(response.routes().averageRouteSpeedMps()).isEqualTo(0.82);
		assertThat(response.routes().dailyMovement()).hasSize(1);
		assertThat(response.routes().dailyMovement().get(0).routeCount()).isEqualTo(12);
		assertThat(response.reports().reportTypeCounts()).containsEntry("STAIRS_STEP", 5L);
		assertThat(response.dataQuality().roadNetworkCompletedRate()).isEqualTo(0.7);
		assertThat(response.dataQuality().facilityCompletedRate()).isEqualTo(0.5);
		assertThat(response.telemetry().enabled()).isFalse();
	}

	@Test
	@DisplayName("관리자 병목 후보는 route session 스냅샷 geometry를 지도 데이터로 변환한다")
	void getBottlenecks() {
		LocalDate from = LocalDate.of(2026, 5, 8);
		LocalDate to = LocalDate.of(2026, 5, 14);
		LocalDateTime start = from.atStartOfDay();
		LocalDateTime endExclusive = to.plusDays(1).atStartOfDay();
		when(routeSessionRepository.findBottleneckRouteCandidates(start, endExclusive, 5))
			.thenReturn(List.of(new BottleneckRouteCandidate(
				"초량 이바구길 입구",
				"LINESTRING(129.039221 35.116888,129.038934 35.115918,129.034979 35.097914)",
				1200.0,
				1500.0,
				16L,
				0.8,
				3L)));

		AdminDashboardService service = new AdminDashboardService(
			userRepository,
			routeSessionRepository,
			hazardReportRepository,
			adminAreaAssignmentRepository,
			roadSegmentRepository,
			placeRepository,
			adminAuditLogService);

		var response = service.getBottlenecks(from, to, 5);

		assertThat(response.telemetryBased()).isFalse();
		assertThat(response.topBottlenecks()).hasSize(1);
		assertThat(response.topBottlenecks().get(0).name()).isEqualTo("초량 이바구길 입구");
		assertThat(response.topBottlenecks().get(0).averageSpeedMps()).isEqualTo(0.8);
		assertThat(response.routeSegments().get(0).points()).hasSize(3);
		assertThat(response.routeSegments().get(0).points().get(0).lat()).isEqualTo(35.116888);
		assertThat(response.routeSegments().get(0).points().get(0).lng()).isEqualTo(129.039221);
	}

	@Test
	@DisplayName("관리자 병목 후보는 generic route title 대신 주변 장소명을 우선 사용한다")
	void getBottlenecksUsesNearbyPlaceNameForGenericRouteTitle() {
		LocalDate from = LocalDate.of(2026, 5, 8);
		LocalDate to = LocalDate.of(2026, 5, 14);
		LocalDateTime start = from.atStartOfDay();
		LocalDateTime endExclusive = to.plusDays(1).atStartOfDay();
		when(routeSessionRepository.findBottleneckRouteCandidates(start, endExclusive, 5))
			.thenReturn(List.of(new BottleneckRouteCandidate(
				"안전 경로",
				"LINESTRING(129.039221 35.116888,129.038934 35.115918,129.034979 35.097914)",
				1200.0,
				1500.0,
				16L,
				0.8,
				3L)));
		when(placeRepository.findNearestPlaceName(anyDouble(), anyDouble(), eq(120)))
			.thenReturn(Optional.of("초량 이바구길 입구"));

		AdminDashboardService service = new AdminDashboardService(
			userRepository,
			routeSessionRepository,
			hazardReportRepository,
			adminAreaAssignmentRepository,
			roadSegmentRepository,
			placeRepository,
			adminAuditLogService);

		var response = service.getBottlenecks(from, to, 5);

		assertThat(response.topBottlenecks()).hasSize(1);
		assertThat(response.topBottlenecks().get(0).name()).isEqualTo("초량 이바구길 입구 인근");
	}

	private record UserTypeCount(PrimaryUserType userType, long count) implements UserRepository.UserTypeCount {
		@Override
		public PrimaryUserType getUserType() {
			return userType;
		}

		@Override
		public long getCount() {
			return count;
		}
	}

	private record ReportTypeCount(ReportType reportType,
		long count) implements HazardReportRepository.ReportTypeCount {
		@Override
		public ReportType getReportType() {
			return reportType;
		}

		@Override
		public long getCount() {
			return count;
		}
	}

	private record DailyMovementMetric(
		LocalDate metricDate,
		Long routeCount,
		Long activeUserCount) implements RouteSessionRepository.DailyMovementMetric {
		@Override
		public LocalDate getMetricDate() {
			return metricDate;
		}

		@Override
		public Long getRouteCount() {
			return routeCount;
		}

		@Override
		public Long getActiveUserCount() {
			return activeUserCount;
		}
	}

	private record BottleneckRouteCandidate(
		String name,
		String geometry,
		Double distanceMeter,
		Double durationSecond,
		Long sampleCount,
		Double expectedSpeedMps,
		Long reportCount) implements RouteSessionRepository.BottleneckRouteCandidate {
		@Override
		public String getName() {
			return name;
		}

		@Override
		public String getGeometry() {
			return geometry;
		}

		@Override
		public Double getDistanceMeter() {
			return distanceMeter;
		}

		@Override
		public Double getDurationSecond() {
			return durationSecond;
		}

		@Override
		public Long getSampleCount() {
			return sampleCount;
		}

		@Override
		public Double getExpectedSpeedMps() {
			return expectedSpeedMps;
		}

		@Override
		public Long getReportCount() {
			return reportCount;
		}
	}
}
