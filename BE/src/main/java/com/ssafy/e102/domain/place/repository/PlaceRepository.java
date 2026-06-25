package com.ssafy.e102.domain.place.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.e102.domain.place.entity.Place;

public interface PlaceRepository extends JpaRepository<Place, Long> {

	@EntityGraph(attributePaths = "accessibilityFeatures")
	Optional<Place> findWithAccessibilityFeaturesByPlaceId(Long placeId);

	@EntityGraph(attributePaths = "accessibilityFeatures")
	List<Place> findAllByProviderPlaceIdIn(Collection<String> providerPlaceIds);

	@EntityGraph(attributePaths = "accessibilityFeatures")
	List<Place> findAllByPlaceIdIn(Collection<Long> placeIds);

	Optional<Place> findByProviderPlaceId(String providerPlaceId);

	@Query(value = """
		select distinct p.*
		from places p
		join admin_areas aa
			on ST_Intersects(p.point, ST_Buffer(aa.geom::geography, 100)::geometry)
		where aa.gu = :gu
		order by p.place_id asc
		limit :limit
		""", nativeQuery = true)
	List<Place> findAllIntersectingGu(
		@Param("gu")
		String gu,
		@Param("limit")
		int limit);

	@Query(value = """
		select exists (
			select 1
			from places p
			join admin_areas aa
				on ST_Intersects(p.point, ST_Buffer(aa.geom::geography, 100)::geometry)
			where p.place_id = :placeId
				and aa.gu = :gu
		)
		""", nativeQuery = true)
	boolean existsIntersectingGuByPlaceId(
		@Param("placeId")
		Long placeId,
		@Param("gu")
		String gu);

	@Query(value = """
		select distinct p.*
		from places p
		join admin_areas aa
			on ST_Intersects(p.point, ST_Buffer(aa.geom::geography, 100)::geometry)
		where aa.gu = :gu
			and (
				aa.dong = :dong
				or replace(replace(replace(replace(aa.dong, '1동', '동'), '2동', '동'), '3동', '동'), '4동', '동') = :dong
			)
		order by p.place_id asc
		limit :limit
		""", nativeQuery = true)
	List<Place> findAllIntersectingArea(
		@Param("gu")
		String gu,
		@Param("dong")
		String dong,
		@Param("limit")
		int limit);

	@Query(value = """
		select exists (
			select 1
			from places p
			join admin_areas aa
				on ST_Intersects(p.point, ST_Buffer(aa.geom::geography, 100)::geometry)
			where p.place_id = :placeId
				and aa.gu = :gu
				and (
					aa.dong = :dong
					or replace(replace(replace(replace(aa.dong, '1동', '동'), '2동', '동'), '3동', '동'), '4동', '동') = :dong
				)
		)
		""", nativeQuery = true)
	boolean existsIntersectingAreaByPlaceId(
		@Param("placeId")
		Long placeId,
		@Param("gu")
		String gu,
		@Param("dong")
		String dong);

	@Query(value = """
		select p.place_id
		from places p
		where (:categoriesEmpty = true or p.category in (:categories))
			and (:featureTypesEmpty = true or exists (
				select 1
				from place_accessibility_features ef
				where ef.place_id = p.place_id
					and ef.is_available = true
					and ef.feature_type in (:featureTypes)
			))
			and ST_DWithin(
				CAST(p.point AS geography),
				CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS geography),
				:radius
			)
		order by ST_DistanceSphere(p.point, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))
		limit :limit
		""", nativeQuery = true)
	List<Long> findPlaceMarkerIds(
		@Param("lat")
		double lat,
		@Param("lng")
		double lng,
		@Param("radius")
		int radius,
		@Param("categories")
		Collection<String> categories,
		@Param("categoriesEmpty")
		boolean categoriesEmpty,
		@Param("featureTypes")
		Collection<String> featureTypes,
		@Param("featureTypesEmpty")
		boolean featureTypesEmpty,
		@Param("limit")
		int limit);

	@Query(value = """
		select p.name
		from places p
		where ST_DWithin(
			CAST(p.point AS geography),
			CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS geography),
			:radius
		)
		order by ST_DistanceSphere(p.point, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))
		limit 1
		""", nativeQuery = true)
	Optional<String> findNearestPlaceName(
		@Param("lat")
		double lat,
		@Param("lng")
		double lng,
		@Param("radius")
		int radius);
}
