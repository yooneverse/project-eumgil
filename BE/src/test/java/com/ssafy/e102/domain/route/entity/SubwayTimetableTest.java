package com.ssafy.e102.domain.route.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.ssafy.e102.domain.route.type.SubwayServiceDayType;

class SubwayTimetableTest {

	@Test
	void createInitializesScheduleLookupColumns() {
		SubwayTimetable timetable = SubwayTimetable.create(
			"130",
			SubwayServiceDayType.WEEKDAY,
			1,
			"05:32",
			19920,
			"노포");

		assertThat(timetable.getOdsayStationId()).isEqualTo("130");
		assertThat(timetable.getServiceDayType()).isEqualTo(SubwayServiceDayType.WEEKDAY);
		assertThat(timetable.getWayCode()).isEqualTo(1);
		assertThat(timetable.getDepartureTimeText()).isEqualTo("05:32");
		assertThat(timetable.getDepartureSecondOfDay()).isEqualTo(19920);
		assertThat(timetable.getEndStationName()).isEqualTo("노포");
	}
}
