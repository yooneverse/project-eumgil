package com.ssafy.e102.global.exception;

import org.springframework.http.HttpStatus;

public enum CommonErrorCode implements ErrorCode {

	INVALID_INPUT(HttpStatus.BAD_REQUEST, "C4000", "잘못된 입력입니다."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A4010", "인증이 필요합니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "A4030", "권한이 없습니다."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "C4040", "요청한 대상을 찾을 수 없습니다."),
	CONFLICT(HttpStatus.CONFLICT, "C4090", "요청 상태가 충돌합니다."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "I5000", "서버 내부 오류가 발생했습니다."),
	EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "E5020", "외부 API 호출에 실패했습니다.");

	private final HttpStatus httpStatus;
	private final String status;
	private final String message;

	CommonErrorCode(HttpStatus httpStatus, String status, String message) {
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
