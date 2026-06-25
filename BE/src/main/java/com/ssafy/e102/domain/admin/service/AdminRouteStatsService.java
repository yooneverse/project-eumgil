package com.ssafy.e102.domain.admin.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.e102.domain.admin.dto.response.AdminDashboardBottleneckResponse.BottleneckHotspotResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminDashboardBottleneckResponse.BottleneckRouteSegmentResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminDashboardBottleneckResponse.GeoPointResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminRouteStatsResponse;
import com.ssafy.e102.domain.admin.repository.AdminRouteStatsQueryRepository;
import com.ssafy.e102.domain.admin.repository.AdminRouteStatsQueryRepository.AverageDistanceRow;
import com.ssafy.e102.domain.admin.repository.AdminRouteStatsQueryRepository.DistanceBucketRow;
import com.ssafy.e102.domain.admin.repository.AdminRouteStatsQueryRepository.HeatmapRow;
import com.ssafy.e102.domain.admin.repository.AdminRouteStatsQueryRepository.MobilityBreakdownRow;
import com.ssafy.e102.domain.admin.repository.AdminRouteStatsQueryRepository.SpeedTrendRow;
import com.ssafy.e102.domain.admin.repository.AdminRouteStatsQueryRepository.TopRouteRow;
import com.ssafy.e102.domain.place.repository.PlaceRepository;

@Service
@Transactional(readOnly = true)
public class AdminRouteStatsService {

	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
	private static final List<MobilityMeta> MOBILITY_META = List.of(
		new MobilityMeta("MOBILITY_SUPPORT", "보행약자", "#2f7df6"),
		new MobilityMeta("POWER_WHEELCHAIR", "자동휠체어", "#8b5cf6"),
		new MobilityMeta("MANUAL_WHEELCHAIR", "수동휠체어", "#f59e0b"),
		new MobilityMeta("VISUAL_IMPAIRMENT", "시각장애인", "#16a34a"));
	private static final List<String> HEATMAP_X_LABELS = List.of("00-04", "04-08", "08-12", "12-16", "16-20", "20-24");
	private static final List<String> HEATMAP_Y_LABELS = List.of("월", "화", "수", "목", "금", "토", "일");
	private static final List<String> SPEED_LABELS = List.of("00시", "04시", "08시", "12시", "16시", "20시");
	private static final List<String> DISTANCE_BUCKETS = List.of("~1km", "1~3km", "3~5km", "5~10km", "10km+");
	private static final int ROUTE_MAP_LIMIT = 12;
	private static final int TOP_ROUTE_LIMIT = 10;

	private final AdminRouteStatsQueryRepository queryRepository;
	private final AdminRouteDisplayNameResolver routeDisplayNameResolver;

	public AdminRouteStatsService(
		AdminRouteStatsQueryRepository queryRepository,
		PlaceRepository placeRepository) {
		this.queryRepository = queryRepository;
		this.routeDisplayNameResolver = new AdminRouteDisplayNameResolver(placeRepository);
	}

	public AdminRouteStatsResponse getRouteStats(LocalDate from, LocalDate to) {
		LocalDate normalizedTo = to == null ? LocalDate.now(SERVICE_ZONE) : to;
		LocalDate normalizedFrom = from == null ? normalizedTo.minusDays(6) : from;
		if (normalizedTo.isBefore(normalizedFrom)) {
			normalizedTo = normalizedFrom;
		}
		LocalDateTime start = normalizedFrom.atStartOfDay();
		LocalDateTime endExclusive = normalizedTo.plusDays(1).atStartOfDay();
		String defaultMobility = "ALL";

		long totalTrips = queryRepository.countTrips(start, endExclusive, defaultMobility);
		var breakdownRows = queryRepository.findMobilityBreakdown(start, endExclusive);
		var heatmapRows = queryRepository.findHeatmapRows(start, endExclusive, defaultMobility);
		var speedRows = queryRepository.findSpeedTrendRows(start, endExclusive);
		var distanceRows = queryRepository.findDistanceBucketRows(start, endExclusive);
		var averageDistanceRows = queryRepository.findAverageDistanceRows(start, endExclusive);
		double overallAverageDistanceKm = queryRepository.findOverallAverageDistanceKm(start, endExclusive, defaultMobility);
		var topRouteRows = queryRepository.findTopRouteRows(start, endExclusive, defaultMobility, ROUTE_MAP_LIMIT);

		List<AdminRouteStatsResponse.BreakdownItemResponse> typeBreakdown = buildTypeBreakdown(totalTrips, breakdownRows);
		List<List<Double>> heatmapValues = buildHeatmapValues(heatmapRows);
		List<AdminRouteStatsResponse.SeriesResponse> speedTrendSeries = buildSpeedTrendSeries(speedRows);
		List<AdminRouteStatsResponse.DistanceSeriesResponse> distanceSeries = buildDistanceSeries(distanceRows, breakdownRows);
		List<AdminRouteStatsResponse.AverageDistanceItemResponse> averageDistance = buildAverageDistance(overallAverageDistanceKm, averageDistanceRows);
		RouteMapBundle routeMapBundle = buildRouteMap(topRouteRows, totalTrips);

		return new AdminRouteStatsResponse(
			new AdminRouteStatsResponse.PeriodResponse(normalizedFrom, normalizedTo),
			new AdminRouteStatsResponse.SummaryResponse(totalTrips, "총 이동 건수"),
			new AdminRouteStatsResponse.FiltersResponse(
				List.of(
					new AdminRouteStatsResponse.SelectOptionResponse("ALL", "전체"),
					new AdminRouteStatsResponse.SelectOptionResponse("MOBILITY_SUPPORT", "보행약자"),
					new AdminRouteStatsResponse.SelectOptionResponse("POWER_WHEELCHAIR", "자동휠체어"),
					new AdminRouteStatsResponse.SelectOptionResponse("MANUAL_WHEELCHAIR", "수동휠체어"),
					new AdminRouteStatsResponse.SelectOptionResponse("VISUAL_IMPAIRMENT", "시각장애인")),
				List.of(
					new AdminRouteStatsResponse.SelectOptionResponse("DAILY", "일별"),
					new AdminRouteStatsResponse.SelectOptionResponse("WEEKLY", "주별"),
					new AdminRouteStatsResponse.SelectOptionResponse("MONTHLY", "월별")),
				new AdminRouteStatsResponse.FilterDefaultsResponse("ALL", "DAILY")),
			new AdminRouteStatsResponse.MapResponse(
				"이동 경로 밀도 히트맵",
				"낮음",
				"높음",
				List.of(
					new AdminRouteStatsResponse.SelectOptionResponse("route-density", "경로 밀도"),
					new AdminRouteStatsResponse.SelectOptionResponse("slow-speed", "저속 구간"),
					new AdminRouteStatsResponse.SelectOptionResponse("report-impact", "신고 반영도")),
				"route-density",
				List.of(
					new AdminRouteStatsResponse.SelectOptionResponse("HEATMAP", "히트맵"),
					new AdminRouteStatsResponse.SelectOptionResponse("WAYPOINT", "점 표시만"),
					new AdminRouteStatsResponse.SelectOptionResponse("DENSITY", "밀도 맵")),
				"HEATMAP",
				true,
				routeMapBundle.hotspots(),
				routeMapBundle.routeSegments()),
			"집계 기준: 동일 geometry 경로 세션을 대표 이동축 1건으로 묶어 집계",
			routeMapBundle.topRoutes().stream().limit(TOP_ROUTE_LIMIT).toList(),
			typeBreakdown,
			new AdminRouteStatsResponse.HeatmapMatrixResponse(
				"요일·시간대 이동 분포",
				"요일/시간대 이동량을 0~1 범위 밀도로 정규화한 값입니다.",
				HEATMAP_X_LABELS,
				HEATMAP_Y_LABELS,
				heatmapValues),
			new AdminRouteStatsResponse.SpeedTrendResponse(SPEED_LABELS, speedTrendSeries),
			new AdminRouteStatsResponse.DistanceDistributionResponse(DISTANCE_BUCKETS, distanceSeries),
			averageDistance,
			List.of(
				new AdminRouteStatsResponse.InfoItemResponse("수집 기간", normalizedFrom + " ~ " + normalizedTo),
				new AdminRouteStatsResponse.InfoItemResponse("수집 기준", "route_sessions + users 실데이터 집계"),
				new AdminRouteStatsResponse.InfoItemResponse("활용 주의", "경로명은 장소명 우선, 부족하면 행정동 기반 대표 이동축으로 표기합니다.")));
	}

	private List<AdminRouteStatsResponse.BreakdownItemResponse> buildTypeBreakdown(
		long totalTrips,
		List<MobilityBreakdownRow> rows) {
		Map<String, Long> countsByGroup = new LinkedHashMap<>();
		rows.forEach(row -> countsByGroup.put(row.mobilityGroup(), row.tripCount()));
		List<AdminRouteStatsResponse.BreakdownItemResponse> items = new ArrayList<>();
		for (MobilityMeta meta : MOBILITY_META) {
			long count = countsByGroup.getOrDefault(meta.group(), 0L);
			items.add(new AdminRouteStatsResponse.BreakdownItemResponse(
				meta.label(),
				count,
				ratio(count, totalTrips),
				meta.color()));
		}
		return items;
	}

	private List<List<Double>> buildHeatmapValues(List<HeatmapRow> rows) {
		double[][] values = new double[HEATMAP_Y_LABELS.size()][HEATMAP_X_LABELS.size()];
		long max = 0;
		for (HeatmapRow row : rows) {
			int dayIndex = heatmapDayIndex(row.dayOfWeek());
			if (dayIndex < 0 || row.bucketIndex() < 0 || row.bucketIndex() >= HEATMAP_X_LABELS.size()) {
				continue;
			}
			values[dayIndex][row.bucketIndex()] = row.tripCount();
			max = Math.max(max, row.tripCount());
		}
		List<List<Double>> normalized = new ArrayList<>();
		for (double[] row : values) {
			List<Double> rowValues = new ArrayList<>();
			for (double value : row) {
				rowValues.add(max == 0 ? 0.0 : round(value / max));
			}
			normalized.add(rowValues);
		}
		return normalized;
	}

	private List<AdminRouteStatsResponse.SeriesResponse> buildSpeedTrendSeries(List<SpeedTrendRow> rows) {
		Map<String, double[]> valuesByGroup = new LinkedHashMap<>();
		MOBILITY_META.forEach(meta -> valuesByGroup.put(meta.group(), new double[SPEED_LABELS.size()]));
		for (SpeedTrendRow row : rows) {
			double[] values = valuesByGroup.get(row.mobilityGroup());
			if (values == null || row.bucketIndex() < 0 || row.bucketIndex() >= SPEED_LABELS.size()) {
				continue;
			}
			values[row.bucketIndex()] = round(row.averageSpeedKmh());
		}
		List<AdminRouteStatsResponse.SeriesResponse> series = new ArrayList<>();
		for (MobilityMeta meta : MOBILITY_META) {
			double[] values = valuesByGroup.get(meta.group());
			List<Double> points = new ArrayList<>();
			for (double value : values) {
				points.add(value);
			}
			series.add(new AdminRouteStatsResponse.SeriesResponse(meta.label(), meta.color(), points));
		}
		return series;
	}

	private List<AdminRouteStatsResponse.DistanceSeriesResponse> buildDistanceSeries(
		List<DistanceBucketRow> rows,
		List<MobilityBreakdownRow> breakdownRows) {
		Map<String, Map<String, Long>> countsByGroup = new LinkedHashMap<>();
		Map<String, Long> totalsByGroup = new LinkedHashMap<>();
		breakdownRows.forEach(row -> totalsByGroup.put(row.mobilityGroup(), row.tripCount()));
		rows.forEach(row -> countsByGroup
			.computeIfAbsent(row.mobilityGroup(), key -> new LinkedHashMap<>())
			.put(row.bucketLabel(), row.tripCount()));

		List<AdminRouteStatsResponse.DistanceSeriesResponse> series = new ArrayList<>();
		for (MobilityMeta meta : MOBILITY_META) {
			Map<String, Long> bucketCounts = countsByGroup.getOrDefault(meta.group(), Map.of());
			long total = totalsByGroup.getOrDefault(meta.group(), 0L);
			List<Long> counts = new ArrayList<>();
			List<Double> shares = new ArrayList<>();
			for (String bucket : DISTANCE_BUCKETS) {
				long count = bucketCounts.getOrDefault(bucket, 0L);
				counts.add(count);
				shares.add(ratio(count, total));
			}
			series.add(new AdminRouteStatsResponse.DistanceSeriesResponse(meta.label(), meta.color(), counts, shares));
		}
		return series;
	}

	private List<AdminRouteStatsResponse.AverageDistanceItemResponse> buildAverageDistance(
		double overallAverageDistanceKm,
		List<AverageDistanceRow> rows) {
		Map<String, Double> valuesByGroup = new LinkedHashMap<>();
		rows.forEach(row -> valuesByGroup.put(row.mobilityGroup(), row.averageDistanceKm()));
		List<AdminRouteStatsResponse.AverageDistanceItemResponse> items = new ArrayList<>();
		items.add(new AdminRouteStatsResponse.AverageDistanceItemResponse("전체", round(overallAverageDistanceKm), "#2f7df6"));
		for (MobilityMeta meta : MOBILITY_META) {
			String color = switch (meta.group()) {
				case "MOBILITY_SUPPORT" -> "#56a9f5";
				case "POWER_WHEELCHAIR" -> "#8b5cf6";
				case "MANUAL_WHEELCHAIR" -> "#f59e0b";
				case "VISUAL_IMPAIRMENT" -> "#16a34a";
				default -> meta.color();
			};
			items.add(new AdminRouteStatsResponse.AverageDistanceItemResponse(
				meta.label(),
				round(valuesByGroup.getOrDefault(meta.group(), 0.0)),
				color));
		}
		return items;
	}

	private RouteMapBundle buildRouteMap(List<TopRouteRow> rows, long totalTrips) {
		List<AdminRouteStatsResponse.TopRouteResponse> topRoutes = new ArrayList<>();
		List<BottleneckHotspotResponse> hotspots = new ArrayList<>();
		List<BottleneckRouteSegmentResponse> routeSegments = new ArrayList<>();

		for (int index = 0; index < rows.size(); index++) {
			TopRouteRow row = rows.get(index);
			List<GeoPointResponse> points = parsePoints(row.geometry());
			if (points.size() < 2) {
				continue;
			}
			String id = "route-density-" + Integer.toHexString(row.geometry().hashCode());
			String name = routeName(row, points, index + 1);
			long routeCount = row.sampleCount();
			double share = ratio(routeCount, totalTrips);
			double averageSpeedMps = round(row.averageSpeedMps());

			topRoutes.add(new AdminRouteStatsResponse.TopRouteResponse(
				topRoutes.size() + 1,
				name,
				routeCount,
				share,
				toneForRank(topRoutes.size() + 1)));
			routeSegments.add(new BottleneckRouteSegmentResponse(
				id,
				name,
				points,
				averageSpeedMps,
				row.reportCount(),
				row.sampleCount()));
			GeoPointResponse center = points.get(points.size() / 2);
			hotspots.add(new BottleneckHotspotResponse(
				id + "-center",
				name,
				center.lat(),
				center.lng(),
				averageSpeedMps,
				row.reportCount(),
				row.sampleCount()));
		}

		return new RouteMapBundle(topRoutes, hotspots, routeSegments);
	}

	private List<GeoPointResponse> parsePoints(String geometryText) {
		if (geometryText == null || geometryText.isBlank()) {
			return List.of();
		}
		try {
			Geometry geometry = new WKTReader().read(geometryText);
			List<GeoPointResponse> points = new ArrayList<>();
			for (var coordinate : geometry.getCoordinates()) {
				points.add(new GeoPointResponse(coordinate.y, coordinate.x));
			}
			return points;
		} catch (ParseException exception) {
			return List.of();
		}
	}

	private String routeName(TopRouteRow row, List<GeoPointResponse> points, int fallbackIndex) {
		return routeDisplayNameResolver.resolve(
			row.representativeTitle(),
			points,
			row.startGu(),
			row.startDong(),
			row.endGu(),
			row.endDong(),
			"대표 이동축 " + fallbackIndex);
	}

	private String toneForRank(int rank) {
		if (rank == 1) return "danger";
		if (rank <= 3) return "hot";
		if (rank <= 6) return "warm";
		return "clear";
	}

	private int heatmapDayIndex(int dayOfWeek) {
		return switch (dayOfWeek) {
			case 1 -> 0;
			case 2 -> 1;
			case 3 -> 2;
			case 4 -> 3;
			case 5 -> 4;
			case 6 -> 5;
			case 0 -> 6;
			default -> -1;
		};
	}

	private double ratio(long numerator, long denominator) {
		if (denominator <= 0) {
			return 0.0;
		}
		return round((double)numerator / denominator);
	}

	private double round(double value) {
		return BigDecimal.valueOf(value)
			.setScale(3, RoundingMode.HALF_UP)
			.doubleValue();
	}

	private record MobilityMeta(
		String group,
		String label,
		String color) {
	}

	private record RouteMapBundle(
		List<AdminRouteStatsResponse.TopRouteResponse> topRoutes,
		List<BottleneckHotspotResponse> hotspots,
		List<BottleneckRouteSegmentResponse> routeSegments) {
	}
}
