package com.ssafy.e102.domain.admin.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 수동 경로 반영 상태")
public record AdminRoutingApplyStateResponse(
	@Schema(description = "최근 경로 반영 결과 상태", example = "PENDING")
	AdminRoutingApplyStatus routingApplyStatus,
	@Schema(description = "상태 설명 메시지")
	String message,
	@Schema(description = "저장된 변경이 runtime에 아직 반영되지 않았는지 여부")
	boolean dirty,
	@Schema(description = "현재 경로 반영 실행 중인지 여부")
	boolean applying,
	@Schema(description = "마지막 경로 반영 완료 시각")
	LocalDateTime lastAppliedAt) {
}
