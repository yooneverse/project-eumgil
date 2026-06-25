package com.ssafy.e102.domain.route.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.e102.domain.route.entity.OdsayLoadLane;

public interface OdsayLoadLaneRepository extends JpaRepository<OdsayLoadLane, Long> {

	List<OdsayLoadLane> findAllByMapObjIn(Collection<String> mapObjs);

	@Modifying
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@Query(value = """
		INSERT INTO odsay_load_lane (map_obj, lane_geometries)
		VALUES (:mapObj, CAST(:laneGeometries AS jsonb))
		ON CONFLICT (map_obj) DO UPDATE
		SET lane_geometries = EXCLUDED.lane_geometries
		""", nativeQuery = true)
	void upsertLaneGeometries(
		@Param("mapObj")
		String mapObj,
		@Param("laneGeometries")
		String laneGeometries);
}
