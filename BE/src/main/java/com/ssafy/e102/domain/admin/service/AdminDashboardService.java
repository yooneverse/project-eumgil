package com.ssafy.e102.domain.admin.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.TreeMap;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.e102.domain.admin.dto.response.AdminDashboardBottleneckResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminDashboardSummaryResponse;
import com.ssafy.e102.domain.admin.repository.AdminAreaAssignmentRepository;
import com.ssafy.e102.domain.admin.type.AdminAreaAssignmentType;
import com.ssafy.e102.domain.admin.type.AdminAreaWorkStatus;
import com.ssafy.e102.domain.place.repository.PlaceRepository;
import com.ssafy.e102.domain.report.entity.HazardReport;
import com.ssafy.e102.domain.report.repository.HazardReportRepository;
import com.ssafy.e102.domain.report.type.ReportStatus;
import com.ssafy.e102.domain.route.repository.RoadSegmentRepository;
import com.ssafy.e102.domain.route.repository.RouteSessionRepository;
import com.ssafy.e102.domain.route.type.RouteSessionStatus;
import com.ssafy.e102.domain.user.repository.UserRepository;
import com.ssafy.e102.domain.user.type.UserRole;

@Service
@Transactional(readOnly = true)
public class AdminDashboardService {

	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
	private static final String TELEMETRY_DISABLED_MESSAGE = "경로 텔레메트리 수집 후 활성화됩니다.";
	private static final String BOTTLENECK_SOURCE = "route_sessions.route_snapshot_json + hazard_reports";
	private static final int DEFAULT_BOTTLENECK_LIMIT = 12;
	private static final int MAX_BOTTLENECK_LIMIT = 50;
	private static final int MAX_ROUTE_POINTS = 72;

	private final UserRepository userRepository;
	private final RouteSessionRepository routeSessionRepository;
	private final HazardReportRepository hazardReportRepository;
	private final AdminAreaAssignmentRepository adminAreaAssignmentRepository;
	private final RoadSegmentRepository roadSegmentRepository;
	private final PlaceRepository placeRepository;
	private final AdminAuditLogService adminAuditLogService;
	private final AdminRouteDisplayNameResolver routeDisplayNameResolver;

	public AdminDashboardService(
		UserRepository userRepository,
		RouteSessionRepository routeSessionRepository,
		HazardReportRepository hazardReportRepository,
		AdminAreaAssignmentRepository adminAreaAssignmentRepository,
		RoadSegmentRepository roadSegmentRepository,
		PlaceRepository placeRepository,
		AdminAuditLogService adminAuditLogService) {
		this.userRepository = userRepository;
		this.routeSessionRepository = routeSessionRepository;
		this.hazardReportRepository = hazardReportRepository;
		this.adminAreaAssignmentRepository = adminAreaAssignmentRepository;
		this.roadSegmentRepository = roadSegmentRepository;
		this.placeRepository = placeRepository;
		this.adminAuditLogService = adminAuditLogService;
		this.routeDisplayNameResolver = new AdminRouteDisplayNameResolver(placeRepository);
	}

	public AdminDashboardSummaryResponse getSummary(LocalDate from, LocalDate to) {
		LocalDate normalizedTo = to == null ? (from == null ? LocalDate.now(SERVICE_ZONE) : from) : to;
		LocalDate normalizedFrom = from == null ? normalizedTo.minusDays(6) : from;
		if (normalizedTo.isBefore(normalizedFrom)) {
			normalizedTo = normalizedFrom;
		}
		LocalDateTime start = normalizedFrom.atStartOfDay();
		LocalDateTime endExclusive = normalizedTo.plusDays(1).atStartOfDay();
		LocalDateTime activeStart = normalizedTo.minusDays(6).atStartOfDay();

		long totalUsers = userRepository.count();
		long newUsers = userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, endExclusive);
		long adminUsers = userRepository.countByRole(UserRole.ADMIN);
		long routeActiveUsers7d = routeSessionRepository
			.countDistinctUsersByCreatedAtGreaterThanEqualAndCreatedAtLessThan(activeStart, endExclusive);

		long totalNavigationSessions = routeSessionRepository.count();
		long navigationStarted = routeSessionRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
			start,
			endExclusive);
		long navigationCompleted = routeSessionRepository.countByStatusAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(
			RouteSessionStatus.COMPLETED,
			start,
			endExclusive);
		long activeRouteSessions = routeSessionRepository.countByStatus(RouteSessionStatus.ACTIVE);
		Double averageNavigationMinutes = routeSessionRepository.averageCompletedDurationMinutes(start, endExclusive);
		Double averageRouteSpeedMps = routeSessionRepository.averageRouteSpeedMps(start, endExclusive);

		long totalReports = hazardReportRepository.count();
		long newReports = hazardReportRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
			start,
			endExclusive);
		long pendingReports = hazardReportRepository.countByStatus(ReportStatus.PENDING);
		long approvedReports = hazardReportRepository.countByStatus(ReportStatus.APPROVED);
		long rejectedReports = hazardReportRepository.countByStatus(ReportStatus.REJECTED);

		long roadNetworkAssignments = adminAreaAssignmentRepository.countByAssignmentType(
			AdminAreaAssignmentType.ROAD_NETWORK);
		long completedRoadNetworkAssignments = adminAreaAssignmentRepository.countByAssignmentTypeAndStatus(
			AdminAreaAssignmentType.ROAD_NETWORK,
			AdminAreaWorkStatus.COMPLETED);
		long facilityAssignments = adminAreaAssignmentRepository
			.countByAssignmentType(AdminAreaAssignmentType.FACILITY);
		long completedFacilityAssignments = adminAreaAssignmentRepository.countByAssignmentTypeAndStatus(
			AdminAreaAssignmentType.FACILITY,
			AdminAreaWorkStatus.COMPLETED);

		return new AdminDashboardSummaryResponse(
			new AdminDashboardSummaryResponse.PeriodResponse(normalizedFrom, normalizedTo),
			new AdminDashboardSummaryResponse.UserMetricsResponse(
				totalUsers,
				newUsers,
				adminUsers,
				routeActiveUsers7d,
				userTypeCounts()),
			new AdminDashboardSummaryResponse.RouteMetricsResponse(
				totalNavigationSessions,
				navigationStarted,
				navigationCompleted,
				rate(navigationCompleted, navigationStarted),
				averageNavigationMinutes == null ? 0.0 : round(averageNavigationMinutes),
				0,
				activeRouteSessions,
				averageRouteSpeedMps == null ? 0.0 : round(averageRouteSpeedMps),
				dailyMovement(start, endExclusive, normalizedFrom, normalizedTo)),
			new AdminDashboardSummaryResponse.ReportMetricsResponse(
				totalReports,
				newReports,
				pendingReports,
				approvedReports,
				rejectedReports,
				reportTypeCounts()),
			new AdminDashboardSummaryResponse.DataQualityMetricsResponse(
				roadSegmentRepository.count(),
				placeRepository.count(),
				roadNetworkAssignments,
				facilityAssignments,
				rate(completedRoadNetworkAssignments, roadNetworkAssignments),
				rate(completedFacilityAssignments, facilityAssignments)),
			new AdminDashboardSummaryResponse.OperationsMetricsResponse(
				adminAuditLogService.getLogs(null, 5).logs(),
				recentReports()),
			new AdminDashboardSummaryResponse.TelemetryMetricsResponse(false, TELEMETRY_DISABLED_MESSAGE));
	}

	public AdminDashboardBottleneckResponse getBottlenecks(LocalDate from, LocalDate to, Integer limit) {
		LocalDate normalizedTo = to == null ? LocalDate.now(SERVICE_ZONE) : to;
		LocalDate normalizedFrom = from == null ? normalizedTo.minusDays(6) : from;
		if (normalizedTo.isBefore(normalizedFrom)) {
			normalizedTo = normalizedFrom;
		}
		int normalizedLimit = Math.max(1, Math.min(MAX_BOTTLENECK_LIMIT,
			limit == null ? DEFAULT_BOTTLENECK_LIMIT : limit));
		LocalDateTime start = normalizedFrom.atStartOfDay();
		LocalDateTime endExclusive = normalizedTo.plusDays(1).atStartOfDay();

		var candidates = routeSessionRepository.findBottleneckRouteCandidates(
			start,
			endExclusive,
			normalizedLimit);
		var routeSegments = new ArrayList<AdminDashboardBottleneckResponse.BottleneckRouteSegmentResponse>();
		var hotspots = new ArrayList<AdminDashboardBottleneckResponse.BottleneckHotspotResponse>();
		var topBottlenecks = new ArrayList<AdminDashboardBottleneckResponse.TopBottleneckResponse>();
		Map<String, Integer> usedNames = new HashMap<>();

		for (int index = 0; index < candidates.size(); index++) {
			RouteSessionRepository.BottleneckRouteCandidate candidate = candidates.get(index);
			var points = parseRoutePoints(candidate.getGeometry());
			if (points.size() < 2) {
				continue;
			}
			String id = "route-" + Integer.toHexString(candidate.getGeometry().hashCode());
			double speed = round(speed(candidate));
			long sampleCount = valueOrZero(candidate.getSampleCount());
			long reportCount = valueOrZero(candidate.getReportCount());
			String name = uniqueRouteName(routeName(candidate.getName(), points, index + 1), usedNames);

			routeSegments.add(new AdminDashboardBottleneckResponse.BottleneckRouteSegmentResponse(
				id,
				name,
				points,
				speed,
				reportCount,
				sampleCount));
			AdminDashboardBottleneckResponse.GeoPointResponse center = points.get(points.size() / 2);
			hotspots.add(new AdminDashboardBottleneckResponse.BottleneckHotspotResponse(
				id + "-center",
				name,
				center.lat(),
				center.lng(),
				speed,
				reportCount,
				sampleCount));
			topBottlenecks.add(new AdminDashboardBottleneckResponse.TopBottleneckResponse(
				topBottlenecks.size() + 1,
				id,
				name,
				speed,
				reportCount,
				sampleCount));
		}

		return new AdminDashboardBottleneckResponse(
			new AdminDashboardBottleneckResponse.PeriodResponse(normalizedFrom, normalizedTo),
			false,
			BOTTLENECK_SOURCE,
			topBottlenecks,
			hotspots,
			routeSegments);
	}

	private Map<String, Long> userTypeCounts() {
		Map<String, Long> counts = new TreeMap<>();
		userRepository.countByPrimaryUserType()
			.forEach(row -> {
				if (row.getUserType() != null) {
					counts.put(row.getUserType().name(), row.getCount());
				}
			});
		return counts;
	}

	private Map<String, Long> reportTypeCounts() {
		Map<String, Long> counts = new TreeMap<>();
		hazardReportRepository.countByReportType()
			.forEach(row -> {
				if (row.getReportType() != null) {
					counts.put(row.getReportType().name(), row.getCount());
				}
			});
		return counts;
	}

	private java.util.List<AdminDashboardSummaryResponse.DailyMovementMetricResponse> dailyMovement(
		LocalDateTime start,
		LocalDateTime endExclusive,
		LocalDate normalizedFrom,
		LocalDate normalizedTo) {
		Map<LocalDate, RouteSessionRepository.DailyMovementMetric> metrics = new TreeMap<>();
		routeSessionRepository.findDailyMovementMetrics(start, endExclusive)
			.forEach(metric -> metrics.put(metric.getMetricDate(), metric));

		var dailyMovement = new ArrayList<AdminDashboardSummaryResponse.DailyMovementMetricResponse>();
		for (LocalDate date = normalizedFrom; !date.isAfter(normalizedTo); date = date.plusDays(1)) {
			RouteSessionRepository.DailyMovementMetric metric = metrics.get(date);
			dailyMovement.add(new AdminDashboardSummaryResponse.DailyMovementMetricResponse(
				date,
				metric == null ? 0 : valueOrZero(metric.getRouteCount()),
				metric == null ? 0 : valueOrZero(metric.getActiveUserCount())));
		}
		return dailyMovement;
	}

	private java.util.List<AdminDashboardSummaryResponse.RecentReportResponse> recentReports() {
		return hazardReportRepository.findRecentForDashboard(PageRequest.of(0, 3))
			.stream()
			.map(this::toRecentReport)
			.toList();
	}

	private AdminDashboardSummaryResponse.RecentReportResponse toRecentReport(HazardReport hazardReport) {
		Point point = hazardReport.getReportPoint();
		return new AdminDashboardSummaryResponse.RecentReportResponse(
			hazardReport.getReportId(),
			hazardReport.getReportType().name(),
			hazardReport.getDescription(),
			hazardReport.getStatus().name(),
			hazardReport.getCreatedAt(),
			point == null ? 0.0 : point.getY(),
			point == null ? 0.0 : point.getX());
	}

	private double rate(long numerator, long denominator) {
		if (denominator <= 0) {
			return 0.0;
		}
		return round((double)numerator / denominator);
	}

	private double round(double value) {
		return BigDecimal.valueOf(value)
			.setScale(4, RoundingMode.HALF_UP)
			.doubleValue();
	}

	private java.util.List<AdminDashboardBottleneckResponse.GeoPointResponse> parseRoutePoints(String geometryText) {
		if (geometryText == null || geometryText.isBlank()) {
			return java.util.List.of();
		}
		try {
			Geometry geometry = new WKTReader().read(geometryText);
			Coordinate[] coordinates = geometry.getCoordinates();
			var points = new ArrayList<AdminDashboardBottleneckResponse.GeoPointResponse>();
			int interval = Math.max(1, coordinates.length / MAX_ROUTE_POINTS);
			for (int index = 0; index < coordinates.length; index += interval) {
				points.add(toPoint(coordinates[index]));
			}
			if (coordinates.length > 0 && (coordinates.length - 1) % interval != 0) {
				points.add(toPoint(coordinates[coordinates.length - 1]));
			}
			return points;
		} catch (ParseException exception) {
			return java.util.List.of();
		}
	}

	private AdminDashboardBottleneckResponse.GeoPointResponse toPoint(Coordinate coordinate) {
		return new AdminDashboardBottleneckResponse.GeoPointResponse(coordinate.y, coordinate.x);
	}

	private double speed(RouteSessionRepository.BottleneckRouteCandidate candidate) {
		Double expectedSpeedMps = candidate.getExpectedSpeedMps();
		if (expectedSpeedMps != null && expectedSpeedMps > 0) {
			return expectedSpeedMps;
		}
		Double distanceMeter = candidate.getDistanceMeter();
		Double durationSecond = candidate.getDurationSecond();
		if (distanceMeter == null || durationSecond == null || distanceMeter <= 0 || durationSecond <= 0) {
			return 0.0;
		}
		return distanceMeter / durationSecond;
	}

	private long valueOrZero(Long value) {
		return value == null ? 0 : value;
	}

	private String routeName(
		String value,
		List<AdminDashboardBottleneckResponse.GeoPointResponse> points,
		int fallbackIndex) {
		return routeDisplayNameResolver.resolve(
			value,
			points,
			null,
			null,
			null,
			null,
			fallbackRouteName(fallbackIndex));
	}

	private String uniqueRouteName(String baseName, Map<String, Integer> usedNames) {
		int occurrence = usedNames.merge(baseName, 1, Integer::sum);
		if (occurrence <= 1) {
			return baseName;
		}
		return baseName + " (" + occurrence + ")";
	}

	private String fallbackRouteName(int fallbackIndex) {
		return "병목 후보 경로 " + fallbackIndex;
	}
}
