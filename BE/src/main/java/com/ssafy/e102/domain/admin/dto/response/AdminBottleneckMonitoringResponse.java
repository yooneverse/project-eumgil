package com.ssafy.e102.domain.admin.dto.response;

import java.util.List;

import com.ssafy.e102.domain.admin.dto.response.AdminDashboardBottleneckResponse.BottleneckHotspotResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminDashboardBottleneckResponse.BottleneckRouteSegmentResponse;

public record AdminBottleneckMonitoringResponse(
	String title,
	String subtitle,
	String dateRangeLabel,
	String exportLabel,
	List<SummaryCardResponse> summaryCards,
	TrendResponse trend,
	DistributionResponse distribution,
	MapResponse map,
	TableResponse table,
	ImpactTopResponse impactTop) {

	public record SummaryCardResponse(
		String label,
		String valueLabel,
		String deltaLabel,
		String comparisonLabel,
		String tone,
		String icon) {
	}

	public record TrendResponse(
		List<String> labels,
		List<SeriesResponse> series,
		int maxValue) {
	}

	public record SeriesResponse(
		String label,
		String color,
		List<Double> values) {
	}

	public record DistributionResponse(
		long totalCount,
		List<DistributionItemResponse> items) {
	}

	public record DistributionItemResponse(
		String label,
		long count,
		double share,
		String color) {
	}

	public record MapResponse(
		List<BottleneckHotspotResponse> hotspots,
		List<BottleneckRouteSegmentResponse> routeSegments) {
	}

	public record TableResponse(
		TableFiltersResponse filters,
		List<TableRowResponse> rows,
		PaginationResponse pagination) {
	}

	public record TableFiltersResponse(
		String typeLabel,
		String statusLabel,
		String sortLabel,
		String pageSizeLabel) {
	}

	public record TableRowResponse(
		int rank,
		String location,
		String address,
		String typeLabel,
		String typeTone,
		String affectedUsersLabel,
		String statusLabel,
		String statusTone,
		String reportedAt) {
	}

	public record PaginationResponse(
		int currentPage,
		List<Integer> pages) {
	}

	public record ImpactTopResponse(
		String sortLabel,
		List<ImpactItemResponse> items) {
	}

	public record ImpactItemResponse(
		int rank,
		String location,
		String affectedUsersLabel,
		String statusLabel,
		String statusTone) {
	}
}
