package com.ssafy.e102.domain.admin.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminRouteStatsQueryRepository {

	private static final String MOBILITY_GROUP_SQL = """
		case
			when u.selected_primary_user_type = 'LOW_VISION' then 'VISUAL_IMPAIRMENT'
			when u.selected_mobility_subtype = 'POWER_WHEELCHAIR' then 'POWER_WHEELCHAIR'
			when u.selected_mobility_subtype = 'MANUAL_WHEELCHAIR' then 'MANUAL_WHEELCHAIR'
			when u.selected_mobility_subtype = 'OTHER_MOBILITY' then 'MOBILITY_SUPPORT'
			else 'UNKNOWN'
		end
		""";

	private static final String BASE_CTE = """
		with base as (
			select
				rs.session_id,
				rs.created_at,
				timezone('Asia/Seoul', rs.created_at) as created_at_kr,
				""" + MOBILITY_GROUP_SQL + """
				as mobility_group,
				coalesce(nullif(rs.route_snapshot_json ->> 'distanceMeter', '')::double precision, 0) as distance_meter,
				coalesce(nullif(rs.route_snapshot_json ->> 'durationSecond', '')::double precision, 0) as duration_second,
				rs.route_snapshot_json ->> 'geometry' as geometry,
				rs.route_snapshot_json ->> 'title' as route_title,
				rs.route_snapshot_json ->> 'transportMode' as transport_mode
			from route_sessions rs
			join users u on u.user_id = rs.user_id
			where rs.created_at >= :from
				and rs.created_at < :to
		)
		""";

	private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

	public AdminRouteStatsQueryRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
		this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
	}

	public long countTrips(LocalDateTime from, LocalDateTime to, String mobilityFilter) {
		Long value = namedParameterJdbcTemplate.queryForObject(
			BASE_CTE + """
				select count(*)
				from base
				where (:mobility = 'ALL' or mobility_group = :mobility)
				""",
			params(from, to, mobilityFilter),
			Long.class);
		return value == null ? 0 : value;
	}

	public List<MobilityBreakdownRow> findMobilityBreakdown(LocalDateTime from, LocalDateTime to) {
		return namedParameterJdbcTemplate.query(
			BASE_CTE + """
				select mobility_group, count(*) as trip_count
				from base
				where mobility_group <> 'UNKNOWN'
				group by mobility_group
				""",
			params(from, to, "ALL"),
			mobilityBreakdownRowMapper());
	}

	public List<HeatmapRow> findHeatmapRows(LocalDateTime from, LocalDateTime to, String mobilityFilter) {
		return namedParameterJdbcTemplate.query(
			BASE_CTE + """
				select
					extract(dow from created_at_kr)::int as day_of_week,
					floor(extract(hour from created_at_kr) / 4)::int as bucket_index,
					count(*) as trip_count
				from base
				where (:mobility = 'ALL' or mobility_group = :mobility)
				group by 1, 2
				""",
			params(from, to, mobilityFilter),
			(resultSet, rowNum) -> new HeatmapRow(
				resultSet.getInt("day_of_week"),
				resultSet.getInt("bucket_index"),
				resultSet.getLong("trip_count")));
	}

	public List<SpeedTrendRow> findSpeedTrendRows(LocalDateTime from, LocalDateTime to) {
		return namedParameterJdbcTemplate.query(
			BASE_CTE + """
				select
					mobility_group,
					floor(extract(hour from created_at_kr) / 4)::int as bucket_index,
					avg(
						case
							when duration_second > 0 and distance_meter > 0 then (distance_meter / duration_second) * 3.6
							else null
						end
					) as average_speed_kmh
				from base
				where mobility_group <> 'UNKNOWN'
				group by 1, 2
				""",
			params(from, to, "ALL"),
			(resultSet, rowNum) -> new SpeedTrendRow(
				resultSet.getString("mobility_group"),
				resultSet.getInt("bucket_index"),
				resultSet.getDouble("average_speed_kmh")));
	}

	public List<DistanceBucketRow> findDistanceBucketRows(LocalDateTime from, LocalDateTime to) {
		return namedParameterJdbcTemplate.query(
			BASE_CTE + """
				select
					mobility_group,
					case
						when distance_meter < 1000 then '~1km'
						when distance_meter < 3000 then '1~3km'
						when distance_meter < 5000 then '3~5km'
						when distance_meter < 10000 then '5~10km'
						else '10km+'
					end as bucket_label,
					count(*) as trip_count
				from base
				where mobility_group <> 'UNKNOWN'
				group by 1, 2
				""",
			params(from, to, "ALL"),
			(resultSet, rowNum) -> new DistanceBucketRow(
				resultSet.getString("mobility_group"),
				resultSet.getString("bucket_label"),
				resultSet.getLong("trip_count")));
	}

	public List<AverageDistanceRow> findAverageDistanceRows(LocalDateTime from, LocalDateTime to) {
		return namedParameterJdbcTemplate.query(
			BASE_CTE + """
				select mobility_group, avg(distance_meter) / 1000.0 as average_distance_km
				from base
				where mobility_group <> 'UNKNOWN'
				group by mobility_group
				""",
			params(from, to, "ALL"),
			(resultSet, rowNum) -> new AverageDistanceRow(
				resultSet.getString("mobility_group"),
				resultSet.getDouble("average_distance_km")));
	}

	public double findOverallAverageDistanceKm(LocalDateTime from, LocalDateTime to, String mobilityFilter) {
		Double value = namedParameterJdbcTemplate.queryForObject(
			BASE_CTE + """
				select avg(distance_meter) / 1000.0
				from base
				where (:mobility = 'ALL' or mobility_group = :mobility)
				""",
			params(from, to, mobilityFilter),
			Double.class);
		return value == null ? 0.0 : value;
	}

	public List<TopRouteRow> findTopRouteRows(
		LocalDateTime from,
		LocalDateTime to,
		String mobilityFilter,
		int limit) {
		return namedParameterJdbcTemplate.query(
			BASE_CTE + """
				, route_candidates as (
					select
						geometry,
						min(route_title) filter (where route_title is not null and route_title <> '') as representative_title,
						avg(distance_meter) as average_distance_meter,
						avg(duration_second) as average_duration_second,
						count(*) as sample_count
					from base
					where (:mobility = 'ALL' or mobility_group = :mobility)
						and geometry like 'LINESTRING%'
					group by geometry
				)
				select
					rc.geometry,
					rc.representative_title,
					rc.average_distance_meter,
					rc.average_duration_second,
					rc.sample_count,
					case
						when rc.average_duration_second > 0 and rc.average_distance_meter > 0
							then rc.average_distance_meter / rc.average_duration_second
						else 0
					end as average_speed_mps,
					(
						select count(*)
						from hazard_reports hazard_report
						where hazard_report.created_at >= :from
							and hazard_report.created_at < :to
							and ST_DWithin(
								hazard_report.report_point::geography,
								ST_GeomFromText(rc.geometry, 4326)::geography,
								50
							)
					) as report_count,
					start_area.gu as start_gu,
					start_area.dong as start_dong,
					end_area.gu as end_gu,
					end_area.dong as end_dong
				from route_candidates rc
				left join lateral (
					select gu, dong
					from admin_areas
					where ST_Intersects(geom, ST_StartPoint(ST_GeomFromText(rc.geometry, 4326)))
					limit 1
				) start_area on true
				left join lateral (
					select gu, dong
					from admin_areas
					where ST_Intersects(geom, ST_EndPoint(ST_GeomFromText(rc.geometry, 4326)))
					limit 1
				) end_area on true
				order by rc.sample_count desc, average_speed_mps asc
				limit :limit
				""",
			Map.of(
				"from", from,
				"to", to,
				"mobility", mobilityFilter,
				"limit", limit),
			(resultSet, rowNum) -> new TopRouteRow(
				resultSet.getString("geometry"),
				resultSet.getString("representative_title"),
				resultSet.getDouble("average_distance_meter"),
				resultSet.getDouble("average_duration_second"),
				resultSet.getLong("sample_count"),
				resultSet.getDouble("average_speed_mps"),
				resultSet.getLong("report_count"),
				resultSet.getString("start_gu"),
				resultSet.getString("start_dong"),
				resultSet.getString("end_gu"),
				resultSet.getString("end_dong")));
	}

	private Map<String, Object> params(LocalDateTime from, LocalDateTime to, String mobilityFilter) {
		return Map.of(
			"from", from,
			"to", to,
			"mobility", mobilityFilter);
	}

	private RowMapper<MobilityBreakdownRow> mobilityBreakdownRowMapper() {
		return (resultSet, rowNum) -> new MobilityBreakdownRow(
			resultSet.getString("mobility_group"),
			resultSet.getLong("trip_count"));
	}

	public record MobilityBreakdownRow(
		String mobilityGroup,
		long tripCount) {
	}

	public record HeatmapRow(
		int dayOfWeek,
		int bucketIndex,
		long tripCount) {
	}

	public record SpeedTrendRow(
		String mobilityGroup,
		int bucketIndex,
		double averageSpeedKmh) {
	}

	public record DistanceBucketRow(
		String mobilityGroup,
		String bucketLabel,
		long tripCount) {
	}

	public record AverageDistanceRow(
		String mobilityGroup,
		double averageDistanceKm) {
	}

	public record TopRouteRow(
		String geometry,
		String representativeTitle,
		double averageDistanceMeter,
		double averageDurationSecond,
		long sampleCount,
		double averageSpeedMps,
		long reportCount,
		String startGu,
		String startDong,
		String endGu,
		String endDong) {
	}
}
