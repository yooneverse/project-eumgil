package com.ssafy.e102.domain.report.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

public record UpdateHazardRouteReviewRequest(
	@Positive
	Long selectedSegmentEdgeId,
	@Valid
	List<AdminHazardRouteReviewSegmentDraftRequest> segmentDrafts) {
}
