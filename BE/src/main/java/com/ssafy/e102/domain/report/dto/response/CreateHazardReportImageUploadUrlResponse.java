package com.ssafy.e102.domain.report.dto.response;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

public record CreateHazardReportImageUploadUrlResponse(
	String uploadUrl,
	String objectKey,
	@JsonFormat(shape = JsonFormat.Shape.STRING)
	Instant expiresAt) {
}
