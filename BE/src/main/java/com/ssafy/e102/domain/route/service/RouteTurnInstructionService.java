package com.ssafy.e102.domain.route.service;

import java.util.Optional;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.springframework.stereotype.Service;

/**
 * route geometry만으로 step 방향 안내를 계산하는 서비스다.
 *
 * <p>이 서비스는 {@code segment_features}에 의존하지 않는다. Feature row는 road segment 분할/export에만
 * 사용하고, 좌/우회전 instruction은 연속 route 좌표의 signed heading 변화에서 계산한다.
 */
@Service
public class RouteTurnInstructionService {

	private static final double DEFAULT_MIN_TURN_DEGREES = 35.0;

	public Optional<RouteTurnDirection> resolve(Coordinate previous, Coordinate pivot, Coordinate next) {
		return resolve(previous, pivot, next, DEFAULT_MIN_TURN_DEGREES);
	}

	public Optional<RouteTurnDirection> resolve(
		Coordinate previous,
		Coordinate pivot,
		Coordinate next,
		double minTurnDegrees) {
		if (previous == null || pivot == null || next == null) {
			return Optional.empty();
		}

		// incoming/outgoing vector는 pivot 전후의 route 진행 방향을 나타낸다.
		double incomingX = pivot.x - previous.x;
		double incomingY = pivot.y - previous.y;
		double outgoingX = next.x - pivot.x;
		double outgoingY = next.y - pivot.y;
		if (isZeroVector(incomingX, incomingY) || isZeroVector(outgoingX, outgoingY)) {
			return Optional.empty();
		}

		// atan2(cross, dot)은 signed angle을 돌려준다. JTS x/y 좌표계에서는 양수가 좌회전이다.
		double cross = incomingX * outgoingY - incomingY * outgoingX;
		double dot = incomingX * outgoingX + incomingY * outgoingY;
		double signedDegrees = Math.toDegrees(Math.atan2(cross, dot));
		if (Math.abs(signedDegrees) < minTurnDegrees) {
			return Optional.empty();
		}
		return Optional.of(signedDegrees > 0.0 ? RouteTurnDirection.LEFT : RouteTurnDirection.RIGHT);
	}

	public Optional<RouteTurnDirection> resolve(LineString routeGeometry, int pivotCoordinateIndex) {
		if (routeGeometry == null || pivotCoordinateIndex <= 0
			|| pivotCoordinateIndex >= routeGeometry.getNumPoints() - 1) {
			return Optional.empty();
		}
		return resolve(
			routeGeometry.getCoordinateN(pivotCoordinateIndex - 1),
			routeGeometry.getCoordinateN(pivotCoordinateIndex),
			routeGeometry.getCoordinateN(pivotCoordinateIndex + 1));
	}

	private boolean isZeroVector(double x, double y) {
		return x == 0.0 && y == 0.0;
	}
}
