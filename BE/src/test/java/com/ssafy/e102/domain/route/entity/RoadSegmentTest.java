package com.ssafy.e102.domain.route.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

import com.ssafy.e102.domain.route.type.AccessibilityState;
import com.ssafy.e102.domain.route.type.SegmentType;
import com.ssafy.e102.domain.route.type.SurfaceState;
import com.ssafy.e102.domain.route.type.WidthState;

class RoadSegmentTest {

	private final GeometryFactory geometryFactory = new GeometryFactory();

	@Test
	void createInitializesAccessibilityDefaults() {
		LineString geom = geometryFactory.createLineString(new Coordinate[] {
			new Coordinate(128.9360, 35.1200),
			new Coordinate(128.9367, 35.1195)
		});

		RoadSegment roadSegment = RoadSegment.create(1L, 10L, 20L, geom, BigDecimal.valueOf(42.5));

		assertThat(roadSegment.getEdgeId()).isEqualTo(1L);
		assertThat(roadSegment.getFromNodeId()).isEqualTo(10L);
		assertThat(roadSegment.getToNodeId()).isEqualTo(20L);
		assertThat(roadSegment.getGeom()).isSameAs(geom);
		assertThat(roadSegment.getLengthMeter()).isEqualByComparingTo("42.5");
		assertThat(roadSegment.getWalkAccess()).isEqualTo(AccessibilityState.UNKNOWN);
		assertThat(roadSegment.getBrailleBlockState()).isEqualTo(AccessibilityState.UNKNOWN);
		assertThat(roadSegment.getAudioSignalState()).isEqualTo(AccessibilityState.UNKNOWN);
		assertThat(roadSegment.getWidthState()).isEqualTo(WidthState.UNKNOWN);
		assertThat(roadSegment.getSurfaceState()).isEqualTo(SurfaceState.UNKNOWN);
		assertThat(roadSegment.getStairsState()).isEqualTo(AccessibilityState.UNKNOWN);
		assertThat(roadSegment.getSignalState()).isEqualTo(AccessibilityState.UNKNOWN);
		assertThat(roadSegment.getSegmentType()).isEqualTo(SegmentType.SIDE_LINE);
	}
}
