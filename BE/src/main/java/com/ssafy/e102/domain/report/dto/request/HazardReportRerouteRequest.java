package com.ssafy.e102.domain.report.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record HazardReportRerouteRequest(
	@NotBlank
	String routeId,
	@NotNull @Valid
	GeoPointRequest currentPoint,
	@Positive
	Integer activeLegSequence) {

	@JsonAnySetter
	public void rejectUnknownField(String fieldName, Object value) {
		throw new IllegalArgumentException("Unsupported hazard report reroute field: " + fieldName);
	}
}
