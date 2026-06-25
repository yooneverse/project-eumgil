package com.ssafy.e102.domain.route.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * `POST /routes/search/walk`가 받는 외부 API 요청 DTO다.
 *
 * <p>사용자 식별자와 접근성 profile은 body로 받지 않고, controller가 인증 사용자에서 조회한 뒤 service로 넘긴다.
 */
public record WalkRouteSearchRequest(
	@Valid @NotNull(message = "출발지 좌표는 필수입니다.")
	GeoPointRequest startPoint,

	@Valid @NotNull(message = "도착지 좌표는 필수입니다.")
	GeoPointRequest endPoint) {

	@JsonAnySetter
	public void rejectUnknownField(String fieldName, Object value) {
		throw new IllegalArgumentException("허용되지 않는 경로 검색 요청 필드입니다: " + fieldName);
	}
}
