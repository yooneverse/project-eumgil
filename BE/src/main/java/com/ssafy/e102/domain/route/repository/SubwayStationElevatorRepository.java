package com.ssafy.e102.domain.route.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ssafy.e102.domain.route.entity.SubwayStationElevator;

public interface SubwayStationElevatorRepository extends JpaRepository<SubwayStationElevator, Long> {

	List<SubwayStationElevator> findByOdsayStationId(String odsayStationId);
}
