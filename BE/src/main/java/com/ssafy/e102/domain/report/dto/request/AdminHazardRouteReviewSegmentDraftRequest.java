package com.ssafy.e102.domain.report.dto.request;

import com.ssafy.e102.domain.route.type.AccessibilityState;
import com.ssafy.e102.domain.route.type.SurfaceState;
import com.ssafy.e102.domain.route.type.WidthState;

import jakarta.validation.constraints.Positive;

public record AdminHazardRouteReviewSegmentDraftRequest(
	@Positive
	Long edgeId,
	AccessibilityState walkAccess,
	AccessibilityState brailleBlockState,
	AccessibilityState audioSignalState,
	WidthState widthState,
	SurfaceState surfaceState,
	AccessibilityState stairsState,
	AccessibilityState signalState) {
}
