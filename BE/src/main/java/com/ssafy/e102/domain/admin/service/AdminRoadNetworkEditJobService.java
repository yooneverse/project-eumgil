package com.ssafy.e102.domain.admin.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.e102.domain.admin.dto.request.AdminRoadNetworkEditApplyRequest;
import com.ssafy.e102.domain.admin.dto.response.AdminRoadNetworkEditApplyResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminRoadNetworkEditJobResponse;
import com.ssafy.e102.domain.admin.type.AdminRoadNetworkEditJobStatus;
import com.ssafy.e102.global.exception.BusinessException;
import com.ssafy.e102.global.exception.CommonErrorCode;

@Service
public class AdminRoadNetworkEditJobService {

	private static final Logger log = LoggerFactory.getLogger(AdminRoadNetworkEditJobService.class);
	private static final String EDIT_JOB_FAILED_MESSAGE = "편집 반영에 실패했습니다. 서버 로그를 확인해주세요.";

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;
	private final AdminRoadNetworkEditService editService;
	private final TaskExecutor taskExecutor;

	public AdminRoadNetworkEditJobService(
		JdbcTemplate jdbcTemplate,
		ObjectMapper objectMapper,
		AdminRoadNetworkEditService editService,
		@Qualifier("adminRoadNetworkEditTaskExecutor")
		TaskExecutor taskExecutor) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
		this.editService = editService;
		this.taskExecutor = taskExecutor;
	}

	public AdminRoadNetworkEditJobResponse create(UUID userId, AdminRoadNetworkEditApplyRequest request) {
		editService.validateEditableRequest(userId, request);
		int totalEdits = request.edits().size();
		Long jobId = jdbcTemplate.queryForObject(
			"""
				insert into admin_road_network_edit_jobs (
					status,
					request_json,
					total_edits,
					processed_edits
				)
				values (?, cast(? as jsonb), ?, 0)
				returning job_id
				""",
			Long.class,
			AdminRoadNetworkEditJobStatus.PENDING.name(),
			writeJson(request),
			totalEdits);
		try {
			taskExecutor.execute(() -> run(jobId, userId, request));
		} catch (TaskRejectedException exception) {
			markFailed(jobId, "편집 반영 작업 대기열이 가득 찼습니다.");
		}
		return findById(jobId);
	}

	public AdminRoadNetworkEditJobResponse findById(Long jobId) {
		return jdbcTemplate.query(
			"""
				select job_id, status, total_edits, processed_edits, result_json, error_message
				from admin_road_network_edit_jobs
				where job_id = ?
				""",
			resultSet -> {
				if (!resultSet.next()) {
					throw new BusinessException(CommonErrorCode.NOT_FOUND, "편집 반영 작업을 찾을 수 없습니다.");
				}
				return toResponse(resultSet);
			},
			jobId);
	}

	public int failUnfinishedJobsOnStartup() {
		Boolean jobTableExists = jdbcTemplate.queryForObject(
			"select to_regclass('public.admin_road_network_edit_jobs') is not null",
			Boolean.class);
		if (!Boolean.TRUE.equals(jobTableExists)) {
			return 0;
		}
		return jdbcTemplate.update(
			"""
				update admin_road_network_edit_jobs
				set status = ?,
					error_message = ?,
					finished_at = now()
				where status in (?, ?)
				""",
			AdminRoadNetworkEditJobStatus.FAILED.name(),
			"서버 재시작으로 편집 반영 작업이 중단되었습니다.",
			AdminRoadNetworkEditJobStatus.PENDING.name(),
			AdminRoadNetworkEditJobStatus.RUNNING.name());
	}

	private void run(Long jobId, UUID userId, AdminRoadNetworkEditApplyRequest request) {
		try {
			markRunning(jobId);
			AdminRoadNetworkEditApplyResponse result = editService.apply(userId, request);
			markSucceeded(jobId, request.edits().size(), result);
		} catch (Exception exception) {
			markFailed(jobId, exception);
		}
	}

	private void markRunning(Long jobId) {
		jdbcTemplate.update(
			"""
				update admin_road_network_edit_jobs
				set status = ?,
					started_at = now()
				where job_id = ?
				""",
			AdminRoadNetworkEditJobStatus.RUNNING.name(),
			jobId);
	}

	private void markSucceeded(Long jobId, int processedEdits, AdminRoadNetworkEditApplyResponse result) {
		jdbcTemplate.update(
			"""
				update admin_road_network_edit_jobs
				set status = ?,
					processed_edits = ?,
					result_json = cast(? as jsonb),
					error_message = null,
					finished_at = now()
				where job_id = ?
				""",
			AdminRoadNetworkEditJobStatus.SUCCEEDED.name(),
			processedEdits,
			writeJson(result),
			jobId);
	}

	private void markFailed(Long jobId, Exception exception) {
		log.error("event=admin_road_network_edit_job_failed jobId={}", jobId, exception);
		if (exception instanceof BusinessException businessException
			&& businessException.getMessage() != null
			&& !businessException.getMessage().isBlank()) {
			markFailed(jobId, businessException.getMessage());
			return;
		}
		markFailed(jobId, EDIT_JOB_FAILED_MESSAGE);
	}

	private void markFailed(Long jobId, String errorMessage) {
		jdbcTemplate.update(
			"""
					update admin_road_network_edit_jobs
					set status = ?,
						error_message = ?,
						finished_at = now()
					where job_id = ?
				""",
			AdminRoadNetworkEditJobStatus.FAILED.name(),
			errorMessage,
			jobId);
	}

	private AdminRoadNetworkEditJobResponse toResponse(ResultSet resultSet) throws SQLException {
		AdminRoadNetworkEditJobStatus status = AdminRoadNetworkEditJobStatus.valueOf(resultSet.getString("status"));
		AdminRoadNetworkEditApplyResponse result = readResult(resultSet.getString("result_json"));
		String errorMessage = resultSet.getString("error_message");
		return new AdminRoadNetworkEditJobResponse(
			resultSet.getLong("job_id"),
			status,
			resultSet.getInt("total_edits"),
			resultSet.getInt("processed_edits"),
			message(status, errorMessage),
			result);
	}

	private AdminRoadNetworkEditApplyResponse readResult(String json) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			return objectMapper.readValue(json, AdminRoadNetworkEditApplyResponse.class);
		} catch (JsonProcessingException exception) {
			throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "편집 반영 결과를 읽을 수 없습니다.");
		}
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException exception) {
			throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "편집 반영 작업을 저장할 수 없습니다.");
		}
	}

	private String message(AdminRoadNetworkEditJobStatus status, String errorMessage) {
		if (status == AdminRoadNetworkEditJobStatus.FAILED) {
			return errorMessage == null || errorMessage.isBlank() ? "편집 반영에 실패했습니다." : errorMessage;
		}
		if (status == AdminRoadNetworkEditJobStatus.SUCCEEDED) {
			return "편집 반영이 완료되었습니다.";
		}
		if (status == AdminRoadNetworkEditJobStatus.RUNNING) {
			return "편집 반영 중입니다.";
		}
		return "편집 반영 작업이 대기 중입니다.";
	}
}
