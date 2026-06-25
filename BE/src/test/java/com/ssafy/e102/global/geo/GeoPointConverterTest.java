package com.ssafy.e102.global.geo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import com.ssafy.e102.global.geo.dto.GeoPointRequest;
import com.ssafy.e102.global.geo.dto.GeoPointResponse;

class GeoPointConverterTest {

	private final GeoPointConverter geoPointConverter = new GeoPointConverter();

	@Test
	@DisplayName("API 좌표는 JTS Point로 변환할 때 경도를 x, 위도를 y에 넣는다")
	void toPoint() {
		Point point = geoPointConverter.toPoint(new GeoPointRequest(35.1686, 129.0576));

		assertThat(point.getSRID()).isEqualTo(4326);
		assertThat(point.getY()).isEqualTo(35.1686);
		assertThat(point.getX()).isEqualTo(129.0576);
	}

	@Test
	@DisplayName("JTS Point는 API 좌표 응답으로 변환한다")
	void toResponse() {
		Point point = geoPointConverter.toPoint(new GeoPointRequest(35.1152, 129.0422));

		GeoPointResponse response = geoPointConverter.toResponse(point);

		assertThat(response.lat()).isEqualTo(35.1152);
		assertThat(response.lng()).isEqualTo(129.0422);
	}
}
