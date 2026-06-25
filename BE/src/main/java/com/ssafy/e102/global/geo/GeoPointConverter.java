package com.ssafy.e102.global.geo;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Component;

import com.ssafy.e102.global.geo.dto.GeoPointRequest;
import com.ssafy.e102.global.geo.dto.GeoPointResponse;

@Component
public class GeoPointConverter {

	private static final int SRID = 4326;

	private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), SRID);

	public Point toPoint(GeoPointRequest request) {
		Point point = geometryFactory.createPoint(new Coordinate(request.lng(), request.lat()));
		point.setSRID(SRID);
		return point;
	}

	public GeoPointResponse toResponse(Point point) {
		return new GeoPointResponse(point.getY(), point.getX());
	}
}
