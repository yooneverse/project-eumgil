package com.ssafy.e102.domain.route.entity;

import com.ssafy.e102.domain.route.type.SubwayServiceDayType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "subway_timetables", indexes = {
	@Index(name = "idx_stt_next_dep", columnList = "odsay_station_id, service_day_type, way_code, departure_second_of_day")
}, uniqueConstraints = {
	@UniqueConstraint(name = "uk_subway_timetables_lookup", columnNames = {
		"odsay_station_id",
		"service_day_type",
		"way_code",
		"departure_second_of_day",
		"end_station_name"
	})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubwayTimetable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "subway_timetable_id", nullable = false, updatable = false)
	private Long subwayTimetableId;

	@Column(name = "odsay_station_id", nullable = false, length = 30)
	private String odsayStationId;

	@Enumerated(EnumType.STRING)
	@Column(name = "service_day_type", nullable = false, length = 30)
	private SubwayServiceDayType serviceDayType;

	@Column(name = "way_code", nullable = false)
	private Integer wayCode;

	@Column(name = "departure_time_text", nullable = false, length = 10)
	private String departureTimeText;

	@Column(name = "departure_second_of_day", nullable = false)
	private Integer departureSecondOfDay;

	@Column(name = "end_station_name", nullable = false, length = 100)
	private String endStationName;

	public static SubwayTimetable create(
		String odsayStationId,
		SubwayServiceDayType serviceDayType,
		Integer wayCode,
		String departureTimeText,
		Integer departureSecondOfDay,
		String endStationName) {
		SubwayTimetable timetable = new SubwayTimetable();
		timetable.odsayStationId = odsayStationId;
		timetable.serviceDayType = serviceDayType;
		timetable.wayCode = wayCode;
		timetable.departureTimeText = departureTimeText;
		timetable.departureSecondOfDay = departureSecondOfDay;
		timetable.endStationName = endStationName;
		return timetable;
	}
}
