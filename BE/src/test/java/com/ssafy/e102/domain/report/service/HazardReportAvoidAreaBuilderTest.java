package com.ssafy.e102.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;

import com.ssafy.e102.global.geo.GeoDistanceCalculator;

class HazardReportAvoidAreaBuilderTest {

	private final HazardReportAvoidAreaBuilder builder = new HazardReportAvoidAreaBuilder();

	@Test
	@DisplayName("directional avoid area extends 10m backward, 2m forward, and 5m sideways")
	void buildDirectionalAvoidArea() {
		Coordinate projectedReportPoint = new Coordinate(129.0000, 35.0000);
		Coordinate segmentStart = new Coordinate(128.9999, 35.0000);
		Coordinate segmentEnd = new Coordinate(129.0001, 35.0000);

		Polygon polygon = builder.build(projectedReportPoint, segmentStart, segmentEnd);
		Coordinate[] coordinates = polygon.getCoordinates();

		assertThat(coordinates).hasSize(5);
		assertThat(polygon.contains(polygon.getFactory().createPoint(projectedReportPoint))).isTrue();

		Coordinate rearMidpoint = midpoint(coordinates[0], coordinates[3]);
		Coordinate frontMidpoint = midpoint(coordinates[1], coordinates[2]);
		Coordinate frontLeft = coordinates[1];
		Coordinate frontRight = coordinates[2];

		double backwardMeters = GeoDistanceCalculator.distanceMeter(
			projectedReportPoint.y,
			projectedReportPoint.x,
			rearMidpoint.y,
			rearMidpoint.x);
		double forwardMeters = GeoDistanceCalculator.distanceMeter(
			projectedReportPoint.y,
			projectedReportPoint.x,
			frontMidpoint.y,
			frontMidpoint.x);
		double widthMeters = GeoDistanceCalculator.distanceMeter(
			frontLeft.y,
			frontLeft.x,
			frontRight.y,
			frontRight.x);

		assertThat(backwardMeters).isBetween(9.0, 11.0);
		assertThat(forwardMeters).isBetween(1.0, 3.0);
		assertThat(widthMeters).isBetween(9.0, 11.0);
	}

	private Coordinate midpoint(Coordinate first, Coordinate second) {
		return new Coordinate(
			(first.x + second.x) / 2.0,
			(first.y + second.y) / 2.0);
	}
}
