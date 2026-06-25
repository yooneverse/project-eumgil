package com.ssafy.e102.domain.auth.exception;

import org.springframework.http.HttpStatus;

import com.ssafy.e102.global.exception.ErrorCode;

public enum AuthErrorCode implements ErrorCode {

	INVALID_AUTH_REQUEST(HttpStatus.BAD_REQUEST, "A4000", "잘못된 입력입니다."),
	INVALID_SOCIAL_TOKEN(HttpStatus.UNAUTHORIZED, "A4011", "인증에 실패했습니다."),
	INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "A4012", "인증이 필요합니다."),
	INVALID_SIGNUP_TOKEN(HttpStatus.UNAUTHORIZED, "A4013", "회원가입 토큰이 유효하지 않습니다."),
	ALREADY_REGISTERED_SOCIAL_USER(HttpStatus.CONFLICT, "A4090", "이미 가입된 사용자입니다."),
	TOKEN_STORE_OPERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "A5000", "인증 토큰 저장소 처리에 실패했습니다."),
	SOCIAL_PROVIDER_API_FAILED(HttpStatus.BAD_GATEWAY, "A5020", "소셜 로그인 연동에 실패했습니다.");

	private final HttpStatus httpStatus;
	private final String status;
	private final String message;

	AuthErrorCode(HttpStatus httpStatus, String status, String message) {
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
