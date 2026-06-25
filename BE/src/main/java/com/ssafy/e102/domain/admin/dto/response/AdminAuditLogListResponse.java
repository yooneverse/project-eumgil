package com.ssafy.e102.domain.admin.dto.response;

import java.util.List;

public record AdminAuditLogListResponse(
	List<AdminAuditLogResponse> logs,
	int size,
	Long nextCursor,
	boolean hasNext) {
}
