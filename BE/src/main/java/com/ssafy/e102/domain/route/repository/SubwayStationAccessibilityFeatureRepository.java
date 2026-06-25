package com.ssafy.e102.domain.route.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ssafy.e102.domain.route.entity.SubwayStation;
import com.ssafy.e102.domain.route.entity.SubwayStationAccessibilityFeature;

public interface SubwayStationAccessibilityFeatureRepository
	extends JpaRepository<SubwayStationAccessibilityFeature, Long> {

	List<SubwayStationAccessibilityFeature> findAllBySubwayStation(SubwayStation subwayStation);
}
