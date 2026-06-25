package com.ssafy.e102.domain.admin.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;

public record AdminDashboardSummaryResponse(
	PeriodResponse period,
	UserMetricsResponse users,
	RouteMetricsResponse routes,
	ReportMetricsResponse reports,
	DataQualityMetricsResponse dataQuality,
	OperationsMetricsResponse operations,
	TelemetryMetricsResponse telemetry) {

	public record PeriodResponse(
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
		LocalDate from,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
		LocalDate to) {
	}

	public record UserMetricsResponse(
		long totalUsers,
		long newUsers,
		long adminUsers,
		long routeActiveUsers7d,
		Map<String, Long> userTypeCounts) {
	}

	public record RouteMetricsResponse(
		long totalNavigationSessions,
		long navigationStarted,
		long navigationCompleted,
		double navigationCompletionRate,
		double averageNavigationMinutes,
		long rerouteCount,
		long activeRouteSessions,
		double averageRouteSpeedMps,
		List<DailyMovementMetricResponse> dailyMovement) {
	}

	public record DailyMovementMetricResponse(
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
		LocalDate date,
		long routeCount,
		long activeUserCount) {
	}

	public record ReportMetricsResponse(
		long totalReports,
		long newReports,
		long pendingReports,
		long approvedReports,
		long rejectedReports,
		Map<String, Long> reportTypeCounts) {
	}

	public record DataQualityMetricsResponse(
		long roadSegments,
		long facilities,
		long roadNetworkAssignments,
		long facilityAssignments,
		double roadNetworkCompletedRate,
		double facilityCompletedRate) {
	}

	public record OperationsMetricsResponse(
		List<AdminAuditLogResponse> recentAuditLogs,
		List<RecentReportResponse> recentReports) {
	}

	public record RecentReportResponse(
		long reportId,
		String reportType,
		String description,
		String status,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
		LocalDateTime createdAt,
		double lat,
		double lng) {
	}

	public record TelemetryMetricsResponse(
		boolean enabled,
		String message) {
	}
}
