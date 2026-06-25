package com.ssafy.e102.domain.report.dto.request;

import com.ssafy.e102.domain.report.type.HazardRouteReviewIntent;

import jakarta.validation.constraints.NotNull;

public record StartHazardRouteReviewRequest(
	@NotNull
	HazardRouteReviewIntent intent) {
}
