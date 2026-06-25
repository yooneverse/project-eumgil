package com.ssafy.e102.domain.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 보행 네트워크 연결 후보 요약")
public record AdminRoadNetworkBridgeSummaryResponse(
	@Schema(description = "선택 구/동 내 연결 컴포넌트 수", example = "8")
	Integer componentCount,
	@Schema(description = "차수 1 이하 endpoint 수", example = "41")
	Integer endpointCount,
	@Schema(description = "전체 연결 후보 수", example = "12")
	Integer bridgeCandidateCount,
	@Schema(description = "지도에 표시되는 연결 후보 수", example = "12")
	Integer visibleBridgeCandidateCount,
	@Schema(description = "연결 후보 최대 거리(m)", example = "50")
	Double bridgeMaxDistanceMeter,
	@Schema(description = "자동 적용 후보 거리 기준(m)", example = "12")
	Double bridgeAutoDistanceMeter) {
}
