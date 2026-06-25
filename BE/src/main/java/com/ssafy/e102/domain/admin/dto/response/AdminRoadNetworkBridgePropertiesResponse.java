package com.ssafy.e102.domain.admin.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 보행 네트워크 연결 후보 속성")
public record AdminRoadNetworkBridgePropertiesResponse(
	@Schema(description = "연결 후보 ID", example = "bridge-1")
	String candidateId,
	@Schema(description = "연결 후보 타입", example = "PROPOSED_BRIDGE")
	String type,
	@Schema(description = "우선순위", example = "AUTO")
	String priority,
	@Schema(description = "시작 endpoint node ID", example = "123")
	String fromNodeId,
	@Schema(description = "시작 endpoint component ID", example = "1")
	String fromComponentId,
	@Schema(description = "연결 대상 segment ID", example = "456")
	String toEdgeId,
	@Schema(description = "연결 대상 component ID", example = "2")
	String toComponentId,
	@Schema(description = "후보 연결 거리(m)", example = "7.2")
	Double distanceMeter,
	@Schema(description = "지도 표시용 마커 경도/위도")
	List<Double> markerPoint,
	@Schema(description = "후보 산출 사유")
	String reason) {
}
