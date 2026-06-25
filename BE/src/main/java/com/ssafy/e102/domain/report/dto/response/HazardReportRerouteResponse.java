package com.ssafy.e102.domain.report.dto.response;

import com.ssafy.e102.domain.route.dto.response.RouteSummaryResponse;

public record HazardReportRerouteResponse(
	boolean rerouted,
	RouteSummaryResponse route) {
}
