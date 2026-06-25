package com.ssafy.e102.domain.admin.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record AdminAuditLogResponse(
	Long logId,
	UUID actorUserId,
	String action,
	String targetType,
	String targetId,
	String gu,
	String dong,
	String summary,
	JsonNode beforeJson,
	JsonNode afterJson,
	LocalDateTime createdAt) {
}
