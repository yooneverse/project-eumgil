package com.ssafy.e102.domain.user.exception;

import org.springframework.http.HttpStatus;

import com.ssafy.e102.global.exception.ErrorCode;

public enum UserErrorCode implements ErrorCode {

	INVALID_USER_REQUEST(HttpStatus.BAD_REQUEST, "U4000", "사용자 요청값이 올바르지 않습니다."),
	REQUIRED_PROFILE_MISSING(HttpStatus.BAD_REQUEST, "U4001", "필수 프로필 정보를 입력해주세요."),
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U4040", "사용자를 찾을 수 없습니다."),
	ALREADY_REGISTERED_USER(HttpStatus.CONFLICT, "U4090", "이미 가입된 사용자입니다."),
	INVALID_USER_TYPE(HttpStatus.BAD_REQUEST, "U4002", "사용자 유형 조합이 올바르지 않습니다.");

	private final HttpStatus httpStatus;
	private final String status;
	private final String message;

	UserErrorCode(HttpStatus httpStatus, String status, String message) {
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
