package com.ssafy.e102.domain.route.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ssafy.e102.domain.route.entity.SubwayStation;

public interface SubwayStationRepository extends JpaRepository<SubwayStation, Long> {

	Optional<SubwayStation> findByOdsayStationId(String odsayStationId);
}
