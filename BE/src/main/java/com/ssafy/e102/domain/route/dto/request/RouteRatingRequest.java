package com.ssafy.e102.domain.route.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RouteRatingRequest(
	@NotNull
	UUID sessionId,

	@NotNull @Min(1) @Max(5)
	Integer score) {
}
