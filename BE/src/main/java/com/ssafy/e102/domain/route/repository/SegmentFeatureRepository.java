package com.ssafy.e102.domain.route.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ssafy.e102.domain.route.entity.SegmentFeature;

public interface SegmentFeatureRepository extends JpaRepository<SegmentFeature, Long> {

	List<SegmentFeature> findByEdgeId(Long edgeId);

	List<SegmentFeature> findByEdgeIdIn(List<Long> edgeIds);
}
