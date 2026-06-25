package com.ssafy.e102.domain.admin.dto.request;

import com.ssafy.e102.domain.admin.type.AdminRouteProfileGroup;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminRoutePreviewRequest(
	@NotBlank
	String gu,
	@NotBlank
	String dong,
	@NotNull @Valid
	GeoPointRequest startPoint,
	@NotNull @Valid
	GeoPointRequest endPoint,
	@NotNull
	AdminRouteProfileGroup profileGroup) {
}
