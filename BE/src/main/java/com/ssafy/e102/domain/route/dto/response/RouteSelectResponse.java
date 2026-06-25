package com.ssafy.e102.domain.route.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record RouteSelectResponse(
	UUID sessionId,
	BigDecimal totalDistanceMeter,
	Integer totalDurationSecond) {

	public static RouteSelectResponse of(
		RouteSessionResponse sessionResponse,
		RouteSummaryResponse route) {
		return new RouteSelectResponse(
			sessionResponse.sessionId(),
			route.distanceMeter(),
			route.durationSecond());
	}
}
