package com.ssafy.e102.domain.route.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

class SubwayStationTest {

	private final GeometryFactory geometryFactory = new GeometryFactory();

	@Test
	void createInitializesStationMappingColumns() {
		Point point = geometryFactory.createPoint(new Coordinate(129.059170, 35.157918));

		SubwayStation station = SubwayStation.create("130", "서면", "부산 1호선", point);

		assertThat(station.getOdsayStationId()).isEqualTo("130");
		assertThat(station.getStationName()).isEqualTo("서면");
		assertThat(station.getLineName()).isEqualTo("부산 1호선");
		assertThat(station.getPoint()).isSameAs(point);
	}

	@Test
	void createAllowsMissingPointWhenSchedulePayloadHasNoCoordinates() {
		SubwayStation station = SubwayStation.create("130", "서면", "부산 1호선", null);

		assertThat(station.getPoint()).isNull();
	}
}
