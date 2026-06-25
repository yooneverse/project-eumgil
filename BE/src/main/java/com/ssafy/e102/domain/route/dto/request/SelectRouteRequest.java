package com.ssafy.e102.domain.route.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * `POST /routes/{routeId}/select` 요청 DTO다.
 */
public record SelectRouteRequest(
	@NotBlank
	String searchId) {
}
