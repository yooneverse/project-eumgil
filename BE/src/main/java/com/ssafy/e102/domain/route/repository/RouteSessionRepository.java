package com.ssafy.e102.domain.route.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.e102.domain.route.entity.RouteSession;
import com.ssafy.e102.domain.route.type.RouteSessionStatus;

public interface RouteSessionRepository extends JpaRepository<RouteSession, UUID> {

	Optional<RouteSession> findFirstByUser_UserIdAndRouteIdOrderByUpdatedAtDesc(UUID userId, String routeId);

	Optional<RouteSession> findFirstByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(
		UUID userId,
		String routeId,
		RouteSessionStatus status);

	Optional<RouteSession> findFirstByUser_UserIdAndStatusOrderByUpdatedAtDesc(UUID userId, RouteSessionStatus status);

	List<RouteSession> findAllByUser_UserIdAndRouteIdAndStatusOrderByUpdatedAtDesc(
		UUID userId,
		String routeId,
		RouteSessionStatus status);

	Optional<RouteSession> findFirstByRouteIdOrderByUpdatedAtDesc(String routeId);

	long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime from, LocalDateTime to);

	long countByStatus(RouteSessionStatus status);

	long countByStatusAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(
		RouteSessionStatus status,
		LocalDateTime from,
		LocalDateTime to);

	@Query(value = """
		select coalesce(avg(extract(epoch from (updated_at - created_at)) / 60.0), 0)
		from route_sessions
		where status = 'COMPLETED'
			and updated_at >= :from
			and updated_at < :to
		""", nativeQuery = true)
	Double averageCompletedDurationMinutes(
		@Param("from")
		LocalDateTime from,
		@Param("to")
		LocalDateTime to);

	@Query(value = """
		select coalesce(avg(distance_meter / duration_second), 0)
		from (
			select
				nullif(route_snapshot_json ->> 'distanceMeter', '')::double precision as distance_meter,
				nullif(route_snapshot_json ->> 'durationSecond', '')::double precision as duration_second
			from route_sessions
			where created_at >= :from
				and created_at < :to
				and jsonb_exists(route_snapshot_json, 'distanceMeter')
				and jsonb_exists(route_snapshot_json, 'durationSecond')
		) route_metrics
		where distance_meter > 0
			and duration_second > 0
		""", nativeQuery = true)
	Double averageRouteSpeedMps(
		@Param("from")
		LocalDateTime from,
		@Param("to")
		LocalDateTime to);

	@Query(value = """
		select
			cast(created_at as date) as "metricDate",
			count(*) as "routeCount",
			count(distinct user_id) as "activeUserCount"
		from route_sessions
		where created_at >= :from
			and created_at < :to
		group by cast(created_at as date)
		order by "metricDate"
		""", nativeQuery = true)
	List<DailyMovementMetric> findDailyMovementMetrics(
		@Param("from")
		LocalDateTime from,
		@Param("to")
		LocalDateTime to);

	@Query("""
		select count(distinct routeSession.user.userId)
		from RouteSession routeSession
		where routeSession.createdAt >= :from
			and routeSession.createdAt < :to
		""")
	long countDistinctUsersByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
		@Param("from")
		LocalDateTime from,
		@Param("to")
		LocalDateTime to);

	@Query(value = """
		with route_candidates as (
			select
				coalesce(nullif(route_snapshot_json ->> 'title', ''), route_id) as name,
				route_snapshot_json ->> 'geometry' as geometry,
				coalesce(nullif(route_snapshot_json ->> 'distanceMeter', '')::double precision, 0) as distance_meter,
				coalesce(nullif(route_snapshot_json ->> 'durationSecond', '')::double precision, 0) as duration_second,
				count(*) as sample_count
			from route_sessions
			where created_at >= :from
				and created_at < :to
				and route_snapshot_json ->> 'transportMode' = 'WALK'
				and jsonb_exists(route_snapshot_json, 'geometry')
				and route_snapshot_json ->> 'geometry' like 'LINESTRING%'
			group by name, geometry, distance_meter, duration_second
		)
		select
			name,
			geometry,
			distance_meter as "distanceMeter",
			duration_second as "durationSecond",
			sample_count as "sampleCount",
			case
				when duration_second > 0 and distance_meter > 0 then distance_meter / duration_second
				else 0
			end as "expectedSpeedMps",
			(
				select count(*)
				from hazard_reports hazard_report
				where hazard_report.created_at >= :from
					and hazard_report.created_at < :to
					and ST_DWithin(
						hazard_report.report_point::geography,
						ST_GeomFromText(route_candidates.geometry, 4326)::geography,
						50
					)
			) as "reportCount"
		from route_candidates
		order by
			"reportCount" desc,
			sample_count desc,
			"expectedSpeedMps" asc
		limit :limit
		""", nativeQuery = true)
	List<BottleneckRouteCandidate> findBottleneckRouteCandidates(
		@Param("from")
		LocalDateTime from,
		@Param("to")
		LocalDateTime to,
		@Param("limit")
		int limit);

	void deleteAllByUser_UserId(UUID userId);

	interface BottleneckRouteCandidate {
		String getName();

		String getGeometry();

		Double getDistanceMeter();

		Double getDurationSecond();

		Long getSampleCount();

		Double getExpectedSpeedMps();

		Long getReportCount();
	}

	interface DailyMovementMetric {
		LocalDate getMetricDate();

		Long getRouteCount();

		Long getActiveUserCount();
	}
}
