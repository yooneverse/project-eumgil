package com.ssafy.e102.domain.bookmark.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateFavoriteRouteRequest(
	@NotBlank(message = "경로 ID는 필수입니다.")
	String routeId,

	@NotBlank(message = "출발지명은 필수입니다.")
	String startLabel,

	@NotBlank(message = "도착지명은 필수입니다.")
	String endLabel) {
}
