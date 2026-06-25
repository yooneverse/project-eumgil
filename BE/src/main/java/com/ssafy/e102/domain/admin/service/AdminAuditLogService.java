package com.ssafy.e102.domain.admin.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.e102.domain.admin.dto.response.AdminAuditLogListResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminAuditLogResponse;
import com.ssafy.e102.global.exception.BusinessException;
import com.ssafy.e102.global.exception.CommonErrorCode;

@Service
@Transactional(readOnly = true)
public class AdminAuditLogService {

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;

	public AdminAuditLogService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
	}

	public AdminAuditLogListResponse getLogs(Long cursor, int size) {
		return getLogs(cursor, null, null, null, null, size);
	}

	public AdminAuditLogListResponse getLogs(
		Long cursor,
		String action,
		String gu,
		String dong,
		UUID actorUserId,
		int size) {
		int querySize = size + 1;
		List<Object> params = new ArrayList<>();
		StringBuilder sql = new StringBuilder("""
			select
				log_id,
				actor_user_id,
				action,
				target_type,
				target_id,
				gu,
				dong,
				summary,
				before_json::text as before_json,
				after_json::text as after_json,
				created_at
			from admin_audit_logs
			where 1 = 1
			""");
		if (cursor != null) {
			sql.append(" and log_id < ?");
			params.add(cursor);
		}
		String normalizedAction = blankToNull(action);
		if (normalizedAction != null) {
			sql.append(" and action = ?");
			params.add(normalizedAction);
		}
		String normalizedGu = blankToNull(gu);
		if (normalizedGu != null) {
			sql.append(" and gu = ?");
			params.add(normalizedGu);
		}
		String normalizedDong = blankToNull(dong);
		if (normalizedDong != null) {
			sql.append(" and dong = ?");
			params.add(normalizedDong);
		}
		if (actorUserId != null) {
			sql.append(" and actor_user_id = ?");
			params.add(actorUserId);
		}
		sql.append(" order by log_id desc limit ?");
		params.add(querySize);

		List<AdminAuditLogResponse> rows = jdbcTemplate.query(sql.toString(), this::toResponse, params.toArray());
		boolean hasNext = rows.size() > size;
		List<AdminAuditLogResponse> content = hasNext ? rows.subList(0, size) : rows;
		Long nextCursor = hasNext ? content.get(content.size() - 1).logId() : null;
		return new AdminAuditLogListResponse(content, size, nextCursor, hasNext);
	}

	@Transactional
	public void record(
		UUID actorUserId,
		String action,
		String targetType,
		String targetId,
		String gu,
		String dong,
		String summary,
		Object before,
		Object after) {
		String beforeJson = writeNullableJson(before);
		String afterJson = writeNullableJson(after);
		if (Objects.equals(beforeJson, afterJson)) {
			return;
		}

		jdbcTemplate.update(
			"""
				insert into admin_audit_logs (
					actor_user_id,
					action,
					target_type,
					target_id,
					gu,
					dong,
					summary,
					before_json,
					after_json
				)
				values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb))
				""",
			actorUserId,
			requireText(action, "관리자 로그 action은 필수입니다."),
			requireText(targetType, "관리자 로그 targetType은 필수입니다."),
			blankToNull(targetId),
			blankToNull(gu),
			blankToNull(dong),
			requireText(summary, "관리자 로그 summary는 필수입니다."),
			beforeJson,
			afterJson);
	}

	private AdminAuditLogResponse toResponse(ResultSet resultSet, int rowNum) throws SQLException {
		return new AdminAuditLogResponse(
			resultSet.getLong("log_id"),
			resultSet.getObject("actor_user_id", UUID.class),
			resultSet.getString("action"),
			resultSet.getString("target_type"),
			resultSet.getString("target_id"),
			resultSet.getString("gu"),
			resultSet.getString("dong"),
			resultSet.getString("summary"),
			readNullableJson(resultSet.getString("before_json")),
			readNullableJson(resultSet.getString("after_json")),
			resultSet.getObject("created_at", LocalDateTime.class));
	}

	private JsonNode readNullableJson(String json) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			return objectMapper.readTree(json);
		} catch (JsonProcessingException exception) {
			throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "관리자 변경 로그를 읽을 수 없습니다.");
		}
	}

	private String writeNullableJson(Object value) {
		if (value == null) {
			return null;
		}
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException exception) {
			throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "관리자 변경 로그를 저장할 수 없습니다.");
		}
	}

	private String requireText(String value, String message) {
		String normalized = blankToNull(value);
		if (normalized == null) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, message);
		}
		return normalized;
	}

	private String blankToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
