package com.ssafy.e102.domain.route.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * `POST /routes/reroute` 요청 DTO다.
 *
 * <p>API 계약상 body에는 기존 선택 routeId와 현재 위치만 받는다.
 */
public record RerouteRequest(
	@NotBlank
	String routeId,
	@NotNull @Valid
	GeoPointRequest currentPoint) {

	@JsonAnySetter
	public void rejectUnknownField(String fieldName, Object value) {
		throw new IllegalArgumentException("허용되지 않는 재탐색 요청 필드입니다: " + fieldName);
	}
}
