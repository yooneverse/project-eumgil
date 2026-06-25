package com.ssafy.e102.domain.admin.dto.response;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

public record AdminDashboardBottleneckResponse(
	PeriodResponse period,
	boolean telemetryBased,
	String source,
	List<TopBottleneckResponse> topBottlenecks,
	List<BottleneckHotspotResponse> hotspots,
	List<BottleneckRouteSegmentResponse> routeSegments) {

	public record PeriodResponse(
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
		LocalDate from,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
		LocalDate to) {
	}

	public record TopBottleneckResponse(
		int rank,
		String id,
		String name,
		double averageSpeedMps,
		long reportCount,
		long sampleCount) {
	}

	public record BottleneckHotspotResponse(
		String id,
		String name,
		double lat,
		double lng,
		double averageSpeedMps,
		long reportCount,
		long sampleCount) {
	}

	public record BottleneckRouteSegmentResponse(
		String id,
		String name,
		List<GeoPointResponse> points,
		double averageSpeedMps,
		long reportCount,
		long sampleCount) {
	}

	public record GeoPointResponse(
		double lat,
		double lng) {
	}
}
