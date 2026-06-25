package com.ssafy.e102.domain.route.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.e102.domain.route.entity.RoadSegment;

public interface RoadSegmentRepository extends JpaRepository<RoadSegment, Long> {

	@Query(value = """
		select distinct rs.*
		from road_segments rs
		join admin_areas aa
			on ST_Intersects(rs.geom, ST_Buffer(aa.geom::geography, 100)::geometry)
		where aa.gu = :gu
		order by rs.edge_id asc
		limit :limit
		""", nativeQuery = true)
	List<RoadSegment> findAllIntersectingGu(
		@Param("gu")
		String gu,
		@Param("limit")
		int limit);

	@Query(value = """
		select distinct rs.*
		from road_segments rs
		join admin_areas aa
			on ST_Intersects(rs.geom, ST_Buffer(aa.geom::geography, 100)::geometry)
		where aa.gu = :gu
		order by rs.edge_id asc
		""", nativeQuery = true)
	List<RoadSegment> findAllIntersectingGu(
		@Param("gu")
		String gu);

	@Query(value = """
		select count(distinct rs.edge_id)
		from road_segments rs
		join admin_areas aa
			on ST_Intersects(rs.geom, ST_Buffer(aa.geom::geography, 100)::geometry)
		where aa.gu = :gu
		""", nativeQuery = true)
	long countIntersectingGu(
		@Param("gu")
		String gu);

	@Query(value = """
		select exists (
			select 1
			from road_segments rs
			join admin_areas aa
				on ST_Intersects(rs.geom, ST_Buffer(aa.geom::geography, 100)::geometry)
			where rs.edge_id = :edgeId
				and aa.gu = :gu
		)
		""", nativeQuery = true)
	boolean existsIntersectingGuByEdgeId(
		@Param("edgeId")
		Long edgeId,
		@Param("gu")
		String gu);

	@Query(value = """
		select distinct rs.*
		from road_segments rs
		join admin_areas aa
			on ST_Intersects(rs.geom, ST_Buffer(aa.geom::geography, 100)::geometry)
		where aa.gu = :gu
			and (
				aa.dong = :dong
				or replace(replace(replace(replace(aa.dong, '1동', '동'), '2동', '동'), '3동', '동'), '4동', '동') = :dong
			)
		order by rs.edge_id asc
		limit :limit
		""", nativeQuery = true)
	List<RoadSegment> findAllIntersectingArea(
		@Param("gu")
		String gu,
		@Param("dong")
		String dong,
		@Param("limit")
		int limit);

	@Query(value = """
		select distinct rs.*
		from road_segments rs
		join admin_areas aa
			on ST_Intersects(rs.geom, ST_Buffer(aa.geom::geography, 100)::geometry)
		where aa.gu = :gu
			and (
				aa.dong = :dong
				or replace(replace(replace(replace(aa.dong, '1동', '동'), '2동', '동'), '3동', '동'), '4동', '동') = :dong
			)
		order by rs.edge_id asc
		""", nativeQuery = true)
	List<RoadSegment> findAllIntersectingArea(
		@Param("gu")
		String gu,
		@Param("dong")
		String dong);

	@Query(value = """
		select count(distinct rs.edge_id)
		from road_segments rs
		join admin_areas aa
			on ST_Intersects(rs.geom, ST_Buffer(aa.geom::geography, 100)::geometry)
		where aa.gu = :gu
			and (
				aa.dong = :dong
				or replace(replace(replace(replace(aa.dong, '1동', '동'), '2동', '동'), '3동', '동'), '4동', '동') = :dong
			)
		""", nativeQuery = true)
	long countIntersectingArea(
		@Param("gu")
		String gu,
		@Param("dong")
		String dong);

	@Query(value = """
		select rs.*
		from road_segments rs
		where exists (
				select 1
				from admin_areas aa
				where aa.gu = :gu
					and (
						aa.dong = :dong
						or replace(replace(replace(replace(aa.dong, '1동', '동'), '2동', '동'), '3동', '동'), '4동', '동') = :dong
					)
					and ST_Intersects(rs.geom, ST_Buffer(aa.geom::geography, 100)::geometry)
			)
			and rs.geom && ST_Expand(
				ST_SetSRID(ST_MakePoint(:lng, :lat), 4326),
				cast(:radiusMeter as double precision) / 111320.0
			)
			and ST_DWithin(
				rs.geom::geography,
				ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
				:radiusMeter
			)
		order by ST_Distance(
				rs.geom::geography,
				ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
			) asc,
			rs.edge_id asc
		limit :limit
		""", nativeQuery = true)
	List<RoadSegment> findAllIntersectingAreaWithinRadius(
		@Param("gu")
		String gu,
		@Param("dong")
		String dong,
		@Param("lng")
		double lng,
		@Param("lat")
		double lat,
		@Param("radiusMeter")
		int radiusMeter,
		@Param("limit")
		int limit);

	@Query(value = """
		select count(*)
		from road_segments rs
		where exists (
				select 1
				from admin_areas aa
				where aa.gu = :gu
					and (
						aa.dong = :dong
						or replace(replace(replace(replace(aa.dong, '1동', '동'), '2동', '동'), '3동', '동'), '4동', '동') = :dong
					)
					and ST_Intersects(rs.geom, ST_Buffer(aa.geom::geography, 100)::geometry)
			)
			and rs.geom && ST_Expand(
				ST_SetSRID(ST_MakePoint(:lng, :lat), 4326),
				cast(:radiusMeter as double precision) / 111320.0
			)
			and ST_DWithin(
				rs.geom::geography,
				ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
				:radiusMeter
			)
		""", nativeQuery = true)
	long countIntersectingAreaWithinRadius(
		@Param("gu")
		String gu,
		@Param("dong")
		String dong,
		@Param("lng")
		double lng,
		@Param("lat")
		double lat,
		@Param("radiusMeter")
		int radiusMeter);

	@Query(value = """
		select exists (
			select 1
			from road_segments rs
			join admin_areas aa
				on ST_Intersects(rs.geom, ST_Buffer(aa.geom::geography, 100)::geometry)
			where rs.edge_id = :edgeId
				and aa.gu = :gu
				and (
					aa.dong = :dong
					or replace(replace(replace(replace(aa.dong, '1동', '동'), '2동', '동'), '3동', '동'), '4동', '동') = :dong
				)
		)
		""", nativeQuery = true)
	boolean existsIntersectingAreaByEdgeId(
		@Param("edgeId")
		Long edgeId,
		@Param("gu")
		String gu,
		@Param("dong")
		String dong);
}
