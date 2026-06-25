package com.ssafy.e102.domain.route.dto.response;

/**
 * `POST /routes/reroute` 성공 응답 data다.
 */
public record RerouteResponse(
	RouteSummaryResponse route) {
}
