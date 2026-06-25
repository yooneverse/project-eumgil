package com.ssafy.e102.domain.admin.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminBottleneckMonitoringQueryRepository {

	private static final int CANDIDATE_WINDOW = 24;

	private static final String CANDIDATE_BASE_CTE = """
		with base as (
			select
				rs.session_id,
				rs.user_id,
				rs.created_at,
				rs.route_snapshot_json ->> 'geometry' as geometry,
				rs.route_snapshot_json ->> 'title' as route_title,
				coalesce(nullif(rs.route_snapshot_json ->> 'distanceMeter', '')::double precision, 0) as distance_meter,
				coalesce(nullif(rs.route_snapshot_json ->> 'durationSecond', '')::double precision, 0) as duration_second
			from route_sessions rs
			where rs.created_at >= :from
				and rs.created_at < :to
				and rs.route_snapshot_json ->> 'transportMode' = 'WALK'
				and jsonb_exists(rs.route_snapshot_json, 'geometry')
				and rs.route_snapshot_json ->> 'geometry' like 'LINESTRING%%'
		),
		route_candidate_counts as (
			select
				geometry,
				min(route_title) filter (where route_title is not null and route_title <> '') as representative_title,
				count(*) as sample_count,
				count(distinct user_id) as distinct_users,
				avg(distance_meter) as average_distance_meter,
				avg(duration_second) as average_duration_second
			from base
			group by geometry
		),
		route_candidates as (
			select *
			from route_candidate_counts
			order by sample_count desc, distinct_users desc, average_distance_meter desc
			limit :candidateWindow
		),
		route_geometries as (
			select
				rc.*,
				route_geom,
				route_geom::geography as route_geography,
				ST_Envelope(route_geom) as route_bbox,
				ST_StartPoint(route_geom) as start_point,
				ST_EndPoint(route_geom) as end_point
			from (
				select rc.*, ST_GeomFromText(rc.geometry, 4326) as route_geom
				from route_candidates rc
			) rc
		)
		""";

	private final NamedParameterJdbcTemplate jdbcTemplate;

	public AdminBottleneckMonitoringQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<BottleneckCandidateRow> findCandidateRows(LocalDateTime from, LocalDateTime to) {
		return jdbcTemplate.query(
			CANDIDATE_BASE_CTE + """
				select
					rg.geometry,
					rg.representative_title,
					rg.sample_count,
					rg.distinct_users,
					case
						when rg.average_duration_second > 0 and rg.average_distance_meter > 0
							then rg.average_distance_meter / rg.average_duration_second
						else 0
					end as average_speed_mps,
					coalesce(report_stats.report_count, 0) as report_count,
					coalesce(report_stats.pending_report_count, 0) as pending_report_count,
					coalesce(report_stats.approved_report_count, 0) as approved_report_count,
					coalesce(report_stats.rejected_report_count, 0) as rejected_report_count,
					report_stats.latest_report_at,
					coalesce(report_stats.width_report_issue, false) as width_report_issue,
					coalesce(report_stats.slope_report_issue, false) as slope_report_issue,
					coalesce(report_stats.facility_report_issue, false) as facility_report_issue,
					0 as narrow_segment_count,
					0 as slope_segment_count,
					0 as crossing_segment_count,
					0 as facility_segment_count,
					start_area.gu as start_gu,
					start_area.dong as start_dong,
					end_area.gu as end_gu,
					end_area.dong as end_dong,
					report_stats.representative_address
				from route_geometries rg
				left join lateral (
					select
						count(*) as report_count,
						count(*) filter (where hr.status = 'PENDING') as pending_report_count,
						count(*) filter (where hr.status = 'APPROVED') as approved_report_count,
						count(*) filter (where hr.status = 'REJECTED') as rejected_report_count,
						max(hr.created_at) as latest_report_at,
						bool_or(hr.report_type in ('SIDEWALK_WIDTH', 'SIDEWALK_MISSING')) as width_report_issue,
						bool_or(hr.report_type in ('RAMP', 'STAIRS_STEP')) as slope_report_issue,
						bool_or(hr.report_type in ('BRAILLE_BLOCK', 'OTHER_OBSTACLE')) as facility_report_issue,
						min(hr.address) filter (where hr.address is not null and hr.address <> '') as representative_address
					from hazard_reports hr
					where hr.created_at >= :from
						and hr.created_at < :to
						and hr.report_point && ST_Expand(rg.route_bbox, 0.0007)
						and ST_DWithin(
							hr.report_point::geography,
							rg.route_geography,
							50
						)
				) report_stats on true
				left join lateral (
					select gu, dong
					from admin_areas
					where ST_Intersects(geom, rg.start_point)
					limit 1
				) start_area on true
				left join lateral (
					select gu, dong
					from admin_areas
					where ST_Intersects(geom, rg.end_point)
					limit 1
				) end_area on true
				order by report_count desc, rg.distinct_users desc, rg.sample_count desc, average_speed_mps asc
				""",
			params(from, to),
			(resultSet, rowNum) -> new BottleneckCandidateRow(
				resultSet.getString("geometry"),
				resultSet.getString("representative_title"),
				resultSet.getLong("sample_count"),
				resultSet.getLong("distinct_users"),
				resultSet.getDouble("average_speed_mps"),
				resultSet.getLong("report_count"),
				resultSet.getLong("pending_report_count"),
				resultSet.getLong("approved_report_count"),
				resultSet.getLong("rejected_report_count"),
				resultSet.getTimestamp("latest_report_at") == null ? null
					: resultSet.getTimestamp("latest_report_at").toLocalDateTime(),
				resultSet.getBoolean("width_report_issue"),
				resultSet.getBoolean("slope_report_issue"),
				resultSet.getBoolean("facility_report_issue"),
				resultSet.getLong("narrow_segment_count"),
				resultSet.getLong("slope_segment_count"),
				resultSet.getLong("crossing_segment_count"),
				resultSet.getLong("facility_segment_count"),
				resultSet.getString("start_gu"),
				resultSet.getString("start_dong"),
				resultSet.getString("end_gu"),
				resultSet.getString("end_dong"),
				resultSet.getString("representative_address")));
	}

	public List<DailyTrendRow> findDailyTrendRows(LocalDateTime from, LocalDateTime to) {
		return jdbcTemplate.query(
			"""
				with base as (
					select
						rs.created_at,
						rs.route_snapshot_json ->> 'geometry' as geometry,
						coalesce(nullif(rs.route_snapshot_json ->> 'distanceMeter', '')::double precision, 0) as distance_meter,
						coalesce(nullif(rs.route_snapshot_json ->> 'durationSecond', '')::double precision, 0) as duration_second
					from route_sessions rs
					where rs.created_at >= :from
						and rs.created_at < :to
						and rs.route_snapshot_json ->> 'transportMode' = 'WALK'
						and jsonb_exists(rs.route_snapshot_json, 'geometry')
						and rs.route_snapshot_json ->> 'geometry' like 'LINESTRING%%'
				),
				daily_candidates as (
					select
						timezone('Asia/Seoul', created_at)::date as metric_date,
						geometry,
						count(*) as sample_count,
						avg(distance_meter) as average_distance_meter,
						avg(duration_second) as average_duration_second
					from base
					group by 1, 2
				)
				select
					dc.metric_date,
					count(*) as total_count,
					count(*) filter (
						where dc.sample_count >= 2
							and (
								case
									when dc.average_duration_second > 0 and dc.average_distance_meter > 0
										then dc.average_distance_meter / dc.average_duration_second
									else 0
								end
							) <= 1.0
					) as evidence_count
				from daily_candidates dc
				group by dc.metric_date
				order by dc.metric_date
				""",
			params(from, to),
			(resultSet, rowNum) -> new DailyTrendRow(
				resultSet.getObject("metric_date", LocalDate.class),
				resultSet.getLong("total_count"),
				resultSet.getLong("evidence_count")));
	}

	private Map<String, Object> params(LocalDateTime from, LocalDateTime to) {
		return Map.of(
			"from", from,
			"to", to,
			"candidateWindow", CANDIDATE_WINDOW);
	}

	public record BottleneckCandidateRow(
		String geometry,
		String representativeTitle,
		long sampleCount,
		long distinctUsers,
		double averageSpeedMps,
		long reportCount,
		long pendingReportCount,
		long approvedReportCount,
		long rejectedReportCount,
		LocalDateTime latestReportAt,
		boolean widthReportIssue,
		boolean slopeReportIssue,
		boolean facilityReportIssue,
		long narrowSegmentCount,
		long slopeSegmentCount,
		long crossingSegmentCount,
		long facilitySegmentCount,
		String startGu,
		String startDong,
		String endGu,
		String endDong,
		String representativeAddress) {
	}

	public record DailyTrendRow(
		LocalDate date,
		long totalCount,
		long evidenceCount) {
	}
}
