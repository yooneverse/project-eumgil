package com.ssafy.e102.domain.report.exception;

import org.springframework.http.HttpStatus;

import com.ssafy.e102.global.exception.ErrorCode;

public enum HazardReportErrorCode implements ErrorCode {

	INVALID_HAZARD_REPORT_REQUEST(HttpStatus.BAD_REQUEST, "HR4000", "제보 요청값이 올바르지 않습니다."),
	INVALID_HAZARD_REPORT_IMAGE_UPLOAD_REQUEST(HttpStatus.BAD_REQUEST, "HR4001", "제보 이미지 업로드 요청값이 올바르지 않습니다."),
	INVALID_HAZARD_REPORT_IMAGE_URL(HttpStatus.BAD_REQUEST, "HR4002", "제보 이미지 URL이 올바르지 않습니다."),
	INVALID_HAZARD_ROUTE_REVIEW_REQUEST(HttpStatus.BAD_REQUEST, "HR4003", "제보 경로 검수 요청값이 올바르지 않습니다."),
	HAZARD_REPORT_FORBIDDEN(HttpStatus.FORBIDDEN, "HR4030", "제보에 대한 권한이 없습니다."),
	HAZARD_REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "HR4040", "제보를 찾을 수 없습니다."),
	HAZARD_ROUTE_REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "HR4041", "제보 경로 검수 정보를 찾을 수 없습니다."),
	HAZARD_REPORT_ALREADY_PROCESSED(HttpStatus.CONFLICT, "HR4090", "이미 처리된 제보입니다."),
	HAZARD_REPORT_IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "HR4091", "같은 Idempotency-Key로 다른 제보 요청이 이미 처리되었습니다."),
	HAZARD_ROUTE_REVIEW_CONFLICT(HttpStatus.CONFLICT, "HR4092", "현재 제보 경로 검수 상태로는 요청을 처리할 수 없습니다."),
	HAZARD_REPORT_IMAGE_UPLOAD_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "HR5030", "제보 이미지 업로드 URL을 발급할 수 없습니다.");

	private final HttpStatus httpStatus;
	private final String status;
	private final String message;

	HazardReportErrorCode(HttpStatus httpStatus, String status, String message) {
		this.httpStatus = httpStatus;
		this.status = status;
		this.message = message;
	}

	@Override
	public HttpStatus getHttpStatus() {
		return httpStatus;
	}

	@Override
	public String getStatus() {
		return status;
	}

	@Override
	public String getMessage() {
		return message;
	}
}
