package com.ssafy.e102.domain.admin.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.e102.domain.admin.dto.response.AdminBottleneckMonitoringResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminDashboardBottleneckResponse.BottleneckHotspotResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminDashboardBottleneckResponse.BottleneckRouteSegmentResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminDashboardBottleneckResponse.GeoPointResponse;
import com.ssafy.e102.domain.admin.repository.AdminBottleneckMonitoringQueryRepository;
import com.ssafy.e102.domain.admin.repository.AdminBottleneckMonitoringQueryRepository.BottleneckCandidateRow;
import com.ssafy.e102.domain.admin.repository.AdminBottleneckMonitoringQueryRepository.DailyTrendRow;
import com.ssafy.e102.domain.place.repository.PlaceRepository;

@Service
@Transactional(readOnly = true)
public class AdminBottleneckMonitoringService {

	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
	private static final DateTimeFormatter DATE_LABEL_FORMATTER = DateTimeFormatter.ofPattern("MM.dd");
	private static final DateTimeFormatter RANGE_LABEL_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");
	private static final NumberFormat INTEGER_FORMATTER = NumberFormat.getIntegerInstance();
	private static final DecimalFormat RATE_FORMATTER = new DecimalFormat("0.0%");
	private static final int MAP_LIMIT = 12;
	private static final int TABLE_LIMIT = 10;
	private static final int IMPACT_LIMIT = 5;

	private final AdminBottleneckMonitoringQueryRepository queryRepository;
	private final AdminRouteDisplayNameResolver routeDisplayNameResolver;

	public AdminBottleneckMonitoringService(
		AdminBottleneckMonitoringQueryRepository queryRepository,
		PlaceRepository placeRepository) {
		this.queryRepository = queryRepository;
		this.routeDisplayNameResolver = new AdminRouteDisplayNameResolver(placeRepository);
	}

	public AdminBottleneckMonitoringResponse getMonitoring(LocalDate from, LocalDate to) {
		LocalDate normalizedTo = to == null ? LocalDate.now(SERVICE_ZONE) : to;
		LocalDate normalizedFrom = from == null ? normalizedTo.minusDays(6) : from;
		if (normalizedTo.isBefore(normalizedFrom)) {
			normalizedTo = normalizedFrom;
		}

		long daySpan = ChronoUnit.DAYS.between(normalizedFrom, normalizedTo) + 1;
		LocalDate previousTo = normalizedFrom.minusDays(1);
		LocalDate previousFrom = previousTo.minusDays(daySpan - 1);

		LocalDateTime start = normalizedFrom.atStartOfDay();
		LocalDateTime endExclusive = normalizedTo.plusDays(1).atStartOfDay();
		LocalDateTime previousStart = previousFrom.atStartOfDay();
		LocalDateTime previousEndExclusive = previousTo.plusDays(1).atStartOfDay();

		List<BottleneckCandidateRow> currentCandidates = queryRepository.findCandidateRows(start, endExclusive);
		List<BottleneckCandidateRow> previousCandidates = queryRepository.findCandidateRows(previousStart, previousEndExclusive);
		List<DailyTrendRow> dailyTrendRows = queryRepository.findDailyTrendRows(start, endExclusive);

		StatsSummary currentSummary = summarize(currentCandidates);
		StatsSummary previousSummary = summarize(previousCandidates);

		List<RankedCandidate> rankedCandidates = currentCandidates.stream()
			.map(this::toRankedCandidate)
			.sorted(Comparator
				.comparingLong((RankedCandidate candidate) -> candidate.row().reportCount()).reversed()
				.thenComparing(Comparator.comparingLong((RankedCandidate candidate) -> candidate.row().distinctUsers()).reversed())
				.thenComparing(Comparator.comparingLong((RankedCandidate candidate) -> candidate.row().sampleCount()).reversed())
				.thenComparing(candidate -> candidate.row().averageSpeedMps()))
			.toList();

		return new AdminBottleneckMonitoringResponse(
			"병목구간 통계",
			"실데이터 기반 병목구간 운영 통계입니다.",
			RANGE_LABEL_FORMATTER.format(normalizedFrom) + " ~ " + RANGE_LABEL_FORMATTER.format(normalizedTo),
			"CSV 다운로드",
			buildSummaryCards(currentSummary, previousSummary),
			buildTrend(normalizedFrom, normalizedTo, dailyTrendRows),
			buildDistribution(currentSummary, rankedCandidates),
			buildMap(rankedCandidates),
			buildTable(rankedCandidates),
			buildImpactTop(rankedCandidates));
	}

	private StatsSummary summarize(List<BottleneckCandidateRow> rows) {
		long totalCandidates = rows.size();
		long evidenceCandidates = rows.stream().filter(this::isEvidenceCandidate).count();
		long affectedUsers = rows.stream()
			.filter(this::isEvidenceCandidate)
			.mapToLong(BottleneckCandidateRow::distinctUsers)
			.sum();
		long reviewedCandidates = rows.stream()
			.filter(row -> row.approvedReportCount() + row.rejectedReportCount() > 0)
			.count();
		Map<Category, Long> categoryCounts = new LinkedHashMap<>();
		for (Category category : Category.values()) {
			categoryCounts.put(category, 0L);
		}
		for (BottleneckCandidateRow row : rows) {
			Category category = classify(row);
			categoryCounts.put(category, categoryCounts.get(category) + 1);
		}
		return new StatsSummary(totalCandidates, evidenceCandidates, affectedUsers, reviewedCandidates, categoryCounts);
	}

	private List<AdminBottleneckMonitoringResponse.SummaryCardResponse> buildSummaryCards(
		StatsSummary current,
		StatsSummary previous) {
		return List.of(
			new AdminBottleneckMonitoringResponse.SummaryCardResponse(
				"병목구간 총수",
				formatCount(current.totalCandidates(), "건"),
				deltaLabel(current.totalCandidates(), previous.totalCandidates(), "건"),
				"지난 기간 대비",
				"danger",
				"alert"),
			new AdminBottleneckMonitoringResponse.SummaryCardResponse(
				"실제 병목구간",
				formatCount(current.evidenceCandidates(), "건"),
				deltaLabel(current.evidenceCandidates(), previous.evidenceCandidates(), "건"),
				"지난 기간 대비",
				"warning",
				"fire"),
			new AdminBottleneckMonitoringResponse.SummaryCardResponse(
				"영향을 받은 사용자",
				formatCount(current.affectedUsers(), "명"),
				deltaLabel(current.affectedUsers(), previous.affectedUsers(), "명"),
				"지난 기간 대비",
				"warning",
				"users"),
			new AdminBottleneckMonitoringResponse.SummaryCardResponse(
				"해결 완료",
				formatCount(current.reviewedCandidates(), "건"),
				deltaLabel(current.reviewedCandidates(), previous.reviewedCandidates(), "건"),
				"지난 기간 대비",
				"success",
				"check"));
	}

	private AdminBottleneckMonitoringResponse.TrendResponse buildTrend(
		LocalDate from,
		LocalDate to,
		List<DailyTrendRow> rows) {
		Map<LocalDate, DailyTrendRow> byDate = new LinkedHashMap<>();
		rows.forEach(row -> byDate.put(row.date(), row));

		List<String> labels = new ArrayList<>();
		List<Double> totalValues = new ArrayList<>();
		List<Double> evidenceValues = new ArrayList<>();
		long max = 0;
		for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
			DailyTrendRow row = byDate.get(date);
			long total = row == null ? 0 : row.totalCount();
			long evidence = row == null ? 0 : row.evidenceCount();
			labels.add(DATE_LABEL_FORMATTER.format(date));
			totalValues.add((double)total);
			evidenceValues.add((double)evidence);
			max = Math.max(max, Math.max(total, evidence));
		}
		return new AdminBottleneckMonitoringResponse.TrendResponse(
			labels,
			List.of(
				new AdminBottleneckMonitoringResponse.SeriesResponse("전체", "#4b82f6", totalValues),
				new AdminBottleneckMonitoringResponse.SeriesResponse("심각", "#ff6b6b", evidenceValues)),
			roundTrendMax(max));
	}

	private AdminBottleneckMonitoringResponse.DistributionResponse buildDistribution(
		StatsSummary summary,
		List<RankedCandidate> rankedCandidates) {
		long totalCount = summary.totalCandidates();
		Map<Category, Long> counts = summary.categoryCounts();
		List<AdminBottleneckMonitoringResponse.DistributionItemResponse> items = new ArrayList<>();
		for (Category category : Category.values()) {
			long count = counts.getOrDefault(category, 0L);
			items.add(new AdminBottleneckMonitoringResponse.DistributionItemResponse(
				category.label(),
				count,
				ratio(count, totalCount),
				category.color()));
		}
		return new AdminBottleneckMonitoringResponse.DistributionResponse(totalCount, items);
	}

	private AdminBottleneckMonitoringResponse.MapResponse buildMap(List<RankedCandidate> candidates) {
		List<BottleneckHotspotResponse> hotspots = new ArrayList<>();
		List<BottleneckRouteSegmentResponse> routeSegments = new ArrayList<>();
		int sequence = 0;
		for (RankedCandidate candidate : candidates) {
			if (routeSegments.size() >= MAP_LIMIT) {
				break;
			}
			List<GeoPointResponse> points = parsePoints(candidate.row().geometry());
			if (points.size() < 2) {
				continue;
			}
			String id = "bottleneck-route-" + (++sequence);
			routeSegments.add(new BottleneckRouteSegmentResponse(
				id,
				candidate.name(),
				points,
				round(candidate.row().averageSpeedMps()),
				candidate.row().reportCount(),
				candidate.row().sampleCount()));
			GeoPointResponse center = points.get(points.size() / 2);
			hotspots.add(new BottleneckHotspotResponse(
				id + "-center",
				candidate.name(),
				center.lat(),
				center.lng(),
				round(candidate.row().averageSpeedMps()),
				candidate.row().reportCount(),
				candidate.row().sampleCount()));
		}
		return new AdminBottleneckMonitoringResponse.MapResponse(hotspots, routeSegments);
	}

	private AdminBottleneckMonitoringResponse.TableResponse buildTable(List<RankedCandidate> candidates) {
		List<AdminBottleneckMonitoringResponse.TableRowResponse> rows = new ArrayList<>();
		int rank = 1;
		for (RankedCandidate candidate : candidates) {
			if (rows.size() >= TABLE_LIMIT) {
				break;
			}
			rows.add(new AdminBottleneckMonitoringResponse.TableRowResponse(
				rank++,
				candidate.name(),
				address(candidate.row()),
				candidate.category().label(),
				candidate.category().tableTone(),
				formatCount(candidate.row().distinctUsers(), "명"),
				severityLabel(candidate.severity()),
				candidate.severity().tone(),
				formatDate(candidate.row().latestReportAt())));
		}
		return new AdminBottleneckMonitoringResponse.TableResponse(
			new AdminBottleneckMonitoringResponse.TableFiltersResponse("전체 유형", "전체 상태", "최신순", "10개씩 보기"),
			rows,
			new AdminBottleneckMonitoringResponse.PaginationResponse(1, List.of(1)));
	}

	private AdminBottleneckMonitoringResponse.ImpactTopResponse buildImpactTop(List<RankedCandidate> candidates) {
		List<AdminBottleneckMonitoringResponse.ImpactItemResponse> items = new ArrayList<>();
		List<RankedCandidate> sorted = candidates.stream()
			.sorted(Comparator
				.comparingLong((RankedCandidate candidate) -> candidate.row().distinctUsers()).reversed()
				.thenComparing(Comparator.comparingLong((RankedCandidate candidate) -> candidate.row().sampleCount()).reversed())
				.thenComparing(candidate -> candidate.row().averageSpeedMps()))
			.toList();
		int rank = 1;
		for (RankedCandidate candidate : sorted) {
			if (items.size() >= IMPACT_LIMIT) {
				break;
			}
			items.add(new AdminBottleneckMonitoringResponse.ImpactItemResponse(
				rank++,
				candidate.name(),
				formatCount(candidate.row().distinctUsers(), "명"),
				severityLabel(candidate.severity()),
				candidate.severity().tone()));
		}
		return new AdminBottleneckMonitoringResponse.ImpactTopResponse("영향 사용자", items);
	}

	private RankedCandidate toRankedCandidate(BottleneckCandidateRow row) {
		List<GeoPointResponse> points = parsePoints(row.geometry());
		return new RankedCandidate(
			routeName(row, points),
			classify(row),
			severity(row),
			row);
	}

	private Category classify(BottleneckCandidateRow row) {
		if (row.widthReportIssue()) {
			return Category.NARROW;
		}
		if (row.slopeReportIssue()) {
			return Category.SLOPE;
		}
		if (row.facilityReportIssue()) {
			return Category.FACILITY;
		}
		long max = Math.max(
			Math.max(row.narrowSegmentCount(), row.slopeSegmentCount()),
			Math.max(row.crossingSegmentCount(), row.facilitySegmentCount()));
		if (max <= 0) {
			return Category.OTHER;
		}
		if (row.narrowSegmentCount() == max) {
			return Category.NARROW;
		}
		if (row.slopeSegmentCount() == max) {
			return Category.SLOPE;
		}
		if (row.crossingSegmentCount() == max) {
			return Category.CROSSING;
		}
		return Category.FACILITY;
	}

	private Severity severity(BottleneckCandidateRow row) {
		if (row.reportCount() >= 3 || (row.averageSpeedMps() > 0 && row.averageSpeedMps() <= 0.8)) {
			return Severity.DANGER;
		}
		if (isEvidenceCandidate(row)) {
			return Severity.WARNING;
		}
		return Severity.NEUTRAL;
	}

	private boolean isEvidenceCandidate(BottleneckCandidateRow row) {
		return row.reportCount() > 0 || (row.averageSpeedMps() > 0 && row.averageSpeedMps() <= 1.0);
	}

	private String routeName(BottleneckCandidateRow row, List<GeoPointResponse> points) {
		return routeDisplayNameResolver.resolve(
			row.representativeTitle(),
			points,
			row.startGu(),
			row.startDong(),
			row.endGu(),
			row.endDong(),
			"대표 이동축");
	}

	private String address(BottleneckCandidateRow row) {
		if (hasText(row.representativeAddress())) {
			return row.representativeAddress();
		}
		if (hasText(row.startGu()) && hasText(row.startDong()) && hasText(row.endGu()) && hasText(row.endDong())) {
			if (row.startGu().equals(row.endGu()) && row.startDong().equals(row.endDong())) {
				return row.startGu() + " " + row.startDong();
			}
			return row.startGu() + " " + row.startDong() + " ↔ " + row.endGu() + " " + row.endDong();
		}
		return "부산광역시";
	}

	private String severityLabel(Severity severity) {
		return switch (severity) {
			case DANGER -> "심각";
			case WARNING -> "주의";
			case NEUTRAL -> "보통";
		};
	}

	private String formatDate(LocalDateTime value) {
		if (value == null) {
			return "-";
		}
		return RANGE_LABEL_FORMATTER.format(value.toLocalDate());
	}

	private String deltaLabel(long current, long previous, String unit) {
		long diff = current - previous;
		double changeRate = previous <= 0
			? (current > 0 ? 1.0 : 0.0)
			: Math.abs((double)diff / previous);
		String prefix = diff > 0 ? "▲ " : diff < 0 ? "▼ " : "― ";
		return prefix + INTEGER_FORMATTER.format(Math.abs(diff)) + unit + " (" + RATE_FORMATTER.format(changeRate) + ")";
	}

	private String formatCount(long value, String unit) {
		return INTEGER_FORMATTER.format(value) + unit;
	}

	private int roundTrendMax(long max) {
		if (max <= 10) {
			return 10;
		}
		long rounded = ((max + 9) / 10) * 10;
		return (int)rounded;
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

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private List<GeoPointResponse> parsePoints(String geometryText) {
		if (!hasText(geometryText)) {
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

	private enum Severity {
		DANGER("danger"),
		WARNING("warning"),
		NEUTRAL("neutral");

		private final String tone;

		Severity(String tone) {
			this.tone = tone;
		}

		public String tone() {
			return tone;
		}
	}

	private enum Category {
		NARROW("좁은 보행로", "#3b82f6", "blue"),
		SLOPE("경사/단차", "#4cc9a6", "green"),
		CROSSING("횡단 주의", "#ffb648", "orange"),
		FACILITY("시설물 장애", "#8b7cf6", "purple"),
		OTHER("기타", "#b9c4d4", "blue");

		private final String label;
		private final String color;
		private final String tableTone;

		Category(String label, String color, String tableTone) {
			this.label = label;
			this.color = color;
			this.tableTone = tableTone;
		}

		public String label() {
			return label;
		}

		public String color() {
			return color;
		}

		public String tableTone() {
			return tableTone;
		}
	}

	private record StatsSummary(
		long totalCandidates,
		long evidenceCandidates,
		long affectedUsers,
		long reviewedCandidates,
		Map<Category, Long> categoryCounts) {
	}

	private record RankedCandidate(
		String name,
		Category category,
		Severity severity,
		BottleneckCandidateRow row) {
	}
}
