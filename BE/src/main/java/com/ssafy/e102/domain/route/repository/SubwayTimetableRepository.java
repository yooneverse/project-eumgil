package com.ssafy.e102.domain.route.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.e102.domain.route.entity.SubwayTimetable;
import com.ssafy.e102.domain.route.type.SubwayServiceDayType;

public interface SubwayTimetableRepository extends JpaRepository<SubwayTimetable, Long> {

	@Query("""
		SELECT timetable
		FROM SubwayTimetable timetable
		WHERE timetable.odsayStationId = :odsayStationId
			AND timetable.serviceDayType = :serviceDayType
			AND timetable.wayCode = :wayCode
			AND timetable.departureSecondOfDay >= :departureSecondOfDay
		ORDER BY timetable.departureSecondOfDay ASC
		""")
	List<SubwayTimetable> findNextDepartures(
		@Param("odsayStationId")
		String odsayStationId,
		@Param("serviceDayType")
		SubwayServiceDayType serviceDayType,
		@Param("wayCode")
		Integer wayCode,
		@Param("departureSecondOfDay")
		Integer departureSecondOfDay,
		Pageable pageable);

	@Query("""
		SELECT timetable
		FROM SubwayTimetable timetable
		WHERE timetable.odsayStationId = :odsayStationId
			AND timetable.serviceDayType = :serviceDayType
			AND timetable.wayCode = :wayCode
		ORDER BY timetable.departureSecondOfDay ASC
		""")
	List<SubwayTimetable> findFirstDepartures(
		@Param("odsayStationId")
		String odsayStationId,
		@Param("serviceDayType")
		SubwayServiceDayType serviceDayType,
		@Param("wayCode")
		Integer wayCode,
		Pageable pageable);
}
