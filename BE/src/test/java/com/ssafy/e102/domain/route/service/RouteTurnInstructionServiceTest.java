package com.ssafy.e102.domain.route.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

/**
 * route 방향 instruction이 geometry 방향 변화에서 파생되는지 검증한다.
 */
class RouteTurnInstructionServiceTest {

	private final GeometryFactory geometryFactory = new GeometryFactory();
	private final RouteTurnInstructionService routeTurnInstructionService = new RouteTurnInstructionService();

	@Test
	void resolveReturnsTurnLeftFromRouteGeometryDirectionChange() {
		LineString routeGeometry = geometryFactory.createLineString(new Coordinate[] {
			new Coordinate(0.0, 0.0),
			new Coordinate(1.0, 0.0),
			new Coordinate(1.0, 1.0)
		});

		Optional<RouteTurnDirection> direction = routeTurnInstructionService.resolve(routeGeometry, 1);

		assertThat(direction).contains(RouteTurnDirection.LEFT);
	}

	@Test
	void resolveReturnsTurnRightFromContinuousSegmentDirectionChange() {
		Optional<RouteTurnDirection> direction = routeTurnInstructionService.resolve(
			new Coordinate(0.0, 0.0),
			new Coordinate(0.0, 1.0),
			new Coordinate(1.0, 1.0));

		assertThat(direction).contains(RouteTurnDirection.RIGHT);
	}

	@Test
	void resolveIgnoresSmallHeadingChange() {
		Optional<RouteTurnDirection> direction = routeTurnInstructionService.resolve(
			new Coordinate(0.0, 0.0),
			new Coordinate(1.0, 0.0),
			new Coordinate(2.0, 0.2));

		assertThat(direction).isEmpty();
	}

	@Test
	void resolveDoesNotDependOnSegmentFeatureType() {
		Optional<RouteTurnDirection> direction = routeTurnInstructionService.resolve(
			new Coordinate(0.0, 0.0),
			new Coordinate(1.0, 0.0),
			new Coordinate(1.0, -1.0));

		assertThat(direction).contains(RouteTurnDirection.RIGHT);
	}
}
