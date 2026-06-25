package com.ssafy.e102.domain.route.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TransitRefreshRequest(
	@NotNull @Min(1)
	Integer legSequence) {
}
