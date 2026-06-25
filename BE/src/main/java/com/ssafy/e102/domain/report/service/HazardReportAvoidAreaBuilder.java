package com.ssafy.e102.domain.report.service;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Component;

@Component
public class HazardReportAvoidAreaBuilder {

	private static final int SRID = 4326;
	private static final double EARTH_RADIUS_METER = 6_371_000.0;
	private static final double BACKWARD_METER = 10.0;
	private static final double FORWARD_METER = 2.0;
	private static final double SIDE_HALF_WIDTH_METER = 5.0;

	private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), SRID);

	public Polygon build(Coordinate projectedReportPoint, Coordinate segmentStart, Coordinate segmentEnd) {
		double referenceLatRadians = Math.toRadians(projectedReportPoint.y);
		double centerX = toProjectedX(projectedReportPoint.x, referenceLatRadians);
		double centerY = toProjectedY(projectedReportPoint.y);
		double startX = toProjectedX(segmentStart.x, referenceLatRadians);
		double startY = toProjectedY(segmentStart.y);
		double endX = toProjectedX(segmentEnd.x, referenceLatRadians);
		double endY = toProjectedY(segmentEnd.y);

		double directionX = endX - startX;
		double directionY = endY - startY;
		double length = Math.hypot(directionX, directionY);
		if (length == 0.0) {
			directionX = 1.0;
			directionY = 0.0;
			length = 1.0;
		}
		double unitX = directionX / length;
		double unitY = directionY / length;
		double normalX = -unitY;
		double normalY = unitX;

		Coordinate rearLeft = toCoordinate(
			centerX - unitX * BACKWARD_METER + normalX * SIDE_HALF_WIDTH_METER,
			centerY - unitY * BACKWARD_METER + normalY * SIDE_HALF_WIDTH_METER,
			referenceLatRadians);
		Coordinate frontLeft = toCoordinate(
			centerX + unitX * FORWARD_METER + normalX * SIDE_HALF_WIDTH_METER,
			centerY + unitY * FORWARD_METER + normalY * SIDE_HALF_WIDTH_METER,
			referenceLatRadians);
		Coordinate frontRight = toCoordinate(
			centerX + unitX * FORWARD_METER - normalX * SIDE_HALF_WIDTH_METER,
			centerY + unitY * FORWARD_METER - normalY * SIDE_HALF_WIDTH_METER,
			referenceLatRadians);
		Coordinate rearRight = toCoordinate(
			centerX - unitX * BACKWARD_METER - normalX * SIDE_HALF_WIDTH_METER,
			centerY - unitY * BACKWARD_METER - normalY * SIDE_HALF_WIDTH_METER,
			referenceLatRadians);

		return geometryFactory.createPolygon(new Coordinate[] {
			rearLeft,
			frontLeft,
			frontRight,
			rearRight,
			rearLeft
		});
	}

	private double toProjectedX(double lng, double referenceLatRadians) {
		return Math.toRadians(lng) * Math.cos(referenceLatRadians) * EARTH_RADIUS_METER;
	}

	private double toProjectedY(double lat) {
		return Math.toRadians(lat) * EARTH_RADIUS_METER;
	}

	private Coordinate toCoordinate(double projectedX, double projectedY, double referenceLatRadians) {
		return new Coordinate(
			Math.toDegrees(projectedX / (Math.cos(referenceLatRadians) * EARTH_RADIUS_METER)),
			Math.toDegrees(projectedY / EARTH_RADIUS_METER));
	}
}
