package com.ssafy.e102.domain.admin.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.ssafy.e102.domain.route.type.AccessibilityState;
import com.ssafy.e102.domain.route.type.SegmentFeatureType;
import com.ssafy.e102.domain.route.type.SegmentType;
import com.ssafy.e102.domain.route.type.SurfaceState;
import com.ssafy.e102.domain.route.type.WidthState;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 보행 네트워크 segment 속성")
public record AdminRoadSegmentPropertiesResponse(
	@Schema(description = "segment ID", example = "1")
	Long edgeId,
	@Schema(description = "시작 node ID", example = "10")
	Long fromNodeId,
	@Schema(description = "종료 node ID", example = "20")
	Long toNodeId,
	@Schema(description = "segment 유형", example = "SIDE_LINE")
	SegmentType segmentType,
	@Schema(description = "길이 meter", example = "12.35")
	BigDecimal lengthMeter,
	@Schema(description = "평균 경사도 percent", example = "3.25")
	BigDecimal avgSlopePercent,
	@Schema(description = "보도 폭 meter", example = "1.50")
	BigDecimal widthMeter,
	AccessibilityState walkAccess,
	AccessibilityState brailleBlockState,
	AccessibilityState audioSignalState,
	WidthState widthState,
	SurfaceState surfaceState,
	AccessibilityState stairsState,
	AccessibilityState signalState,
	List<SegmentFeatureType> featureTypes) {
}
