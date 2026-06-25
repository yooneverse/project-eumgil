package com.ssafy.e102.domain.report.dto.response;

import com.ssafy.e102.domain.report.entity.HazardReportRouteReviewSegmentDraft;
import com.ssafy.e102.domain.route.type.AccessibilityState;
import com.ssafy.e102.domain.route.type.SurfaceState;
import com.ssafy.e102.domain.route.type.WidthState;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "제보 경로 검수 세그먼트 draft 응답")
public record AdminHazardRouteReviewSegmentDraftResponse(
	@Schema(description = "보행 segment edge ID", example = "41231")
	Long edgeId,
	@Schema(description = "통행 가능 여부", example = "NO")
	AccessibilityState walkAccess,
	@Schema(description = "점자블록 여부", example = "UNKNOWN")
	AccessibilityState brailleBlockState,
	@Schema(description = "음향 신호 여부", example = "UNKNOWN")
	AccessibilityState audioSignalState,
	@Schema(description = "보도폭 상태", example = "NARROW")
	WidthState widthState,
	@Schema(description = "노면 상태", example = "UNKNOWN")
	SurfaceState surfaceState,
	@Schema(description = "계단 여부", example = "YES")
	AccessibilityState stairsState,
	@Schema(description = "신호기 여부", example = "UNKNOWN")
	AccessibilityState signalState) {

	public static AdminHazardRouteReviewSegmentDraftResponse from(HazardReportRouteReviewSegmentDraft draft) {
		return new AdminHazardRouteReviewSegmentDraftResponse(
			draft.getEdgeId(),
			draft.getWalkAccess(),
			draft.getBrailleBlockState(),
			draft.getAudioSignalState(),
			draft.getWidthState(),
			draft.getSurfaceState(),
			draft.getStairsState(),
			draft.getSignalState());
	}
}
