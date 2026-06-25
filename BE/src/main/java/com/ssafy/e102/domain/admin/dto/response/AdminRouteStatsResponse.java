package com.ssafy.e102.domain.admin.dto.response;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ssafy.e102.domain.admin.dto.response.AdminDashboardBottleneckResponse.BottleneckHotspotResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminDashboardBottleneckResponse.BottleneckRouteSegmentResponse;

public record AdminRouteStatsResponse(
	PeriodResponse period,
	SummaryResponse summary,
	FiltersResponse filters,
	MapResponse map,
	String topRoutesDefinition,
	List<TopRouteResponse> topRoutes,
	List<BreakdownItemResponse> typeBreakdown,
	HeatmapMatrixResponse hourlyHeatmap,
	SpeedTrendResponse speedTrend,
	DistanceDistributionResponse distanceDistribution,
	List<AverageDistanceItemResponse> averageDistance,
	List<InfoItemResponse> infoItems) {

	public record PeriodResponse(
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
		LocalDate from,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
		LocalDate to) {
	}

	public record SummaryResponse(
		long totalTrips,
		String metricLabel) {
	}

	public record FiltersResponse(
		List<SelectOptionResponse> mobilityOptions,
		List<SelectOptionResponse> timeGranularityOptions,
		FilterDefaultsResponse defaults) {
	}

	public record SelectOptionResponse(
		String value,
		String label) {
	}

	public record FilterDefaultsResponse(
		String mobility,
		String timeGranularity) {
	}

	public record MapResponse(
		String title,
		String legendMinLabel,
		String legendMaxLabel,
		List<SelectOptionResponse> metricOptions,
		String selectedMetric,
		List<SelectOptionResponse> modeOptions,
		String selectedMode,
		boolean showDistrictBoundary,
		List<BottleneckHotspotResponse> hotspots,
		List<BottleneckRouteSegmentResponse> routeSegments) {
	}

	public record TopRouteResponse(
		int rank,
		String name,
		long routeCount,
		double share,
		String tone) {
	}

	public record BreakdownItemResponse(
		String label,
		long count,
		double share,
		String color) {
	}

	public record HeatmapMatrixResponse(
		String title,
		String helperText,
		List<String> xLabels,
		List<String> yLabels,
		List<List<Double>> values) {
	}

	public record SpeedTrendResponse(
		List<String> labels,
		List<SeriesResponse> series) {
	}

	public record SeriesResponse(
		String label,
		String color,
		List<Double> values) {
	}

	public record DistanceDistributionResponse(
		List<String> buckets,
		List<DistanceSeriesResponse> series) {
	}

	public record DistanceSeriesResponse(
		String label,
		String color,
		List<Long> count,
		List<Double> share) {
	}

	public record AverageDistanceItemResponse(
		String label,
		double kilometer,
		String color) {
	}

	public record InfoItemResponse(
		String label,
		String value) {
	}
}
