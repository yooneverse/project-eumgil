package com.ssafy.e102.domain.route.service;

import com.ssafy.e102.domain.route.dto.response.RouteSummaryResponse;

public record TransitRouteCandidate(
	RouteSummaryResponse route,
	TransitRouteSnapshot snapshot,
	int totalWalkMeter,
	int transferCount) {
}
