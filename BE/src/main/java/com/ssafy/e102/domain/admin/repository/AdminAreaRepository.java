package com.ssafy.e102.domain.admin.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.e102.domain.admin.entity.AdminArea;

public interface AdminAreaRepository extends JpaRepository<AdminArea, Long> {

	@Query("""
		select distinct adminArea.gu, adminArea.dong
		from AdminArea adminArea
		order by adminArea.gu asc, adminArea.dong asc
		""")
	List<Object[]> findDistinctAreas();

	@Query("""
		select count(adminArea) > 0
		from AdminArea adminArea
		where adminArea.gu = :gu
			and (
				adminArea.dong = :dong
				or replace(replace(replace(replace(adminArea.dong, '1동', '동'), '2동', '동'), '3동', '동'), '4동', '동') = :dong
			)
		""")
	boolean existsArea(
		@Param("gu")
		String gu,
		@Param("dong")
		String dong);

	@Query("""
		select count(adminArea) > 0
		from AdminArea adminArea
		where adminArea.gu = :gu
		""")
	boolean existsGu(
		@Param("gu")
		String gu);

	@Query(value = """
		select ST_AsGeoJSON(ST_Boundary(ST_UnaryUnion(ST_Collect(geom))))
		from admin_areas
		where gu = :gu
		""", nativeQuery = true)
	String findGuBoundaryGeoJson(
		@Param("gu")
		String gu);

	@Query(value = """
		select ST_AsGeoJSON(ST_Boundary(ST_UnaryUnion(ST_Collect(geom))))
		from admin_areas
		where gu = :gu
			and (
				dong = :dong
				or replace(replace(replace(replace(dong, '1동', '동'), '2동', '동'), '3동', '동'), '4동', '동') = :dong
			)
		""", nativeQuery = true)
	String findAreaBoundaryGeoJson(
		@Param("gu")
		String gu,
		@Param("dong")
		String dong);

	@Query(value = """
		select gu, dong
		from admin_areas
		where ST_Intersects(
			geom,
			ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)
		)
		order by ST_Area(geom::geography) asc
		limit 1
		""", nativeQuery = true)
	java.util.Optional<Object[]> findAreaByPoint(
		@Param("lng")
		double lng,
		@Param("lat")
		double lat);
}
