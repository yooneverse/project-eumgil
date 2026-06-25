package com.ssafy.e102.global.response;

import com.ssafy.e102.global.exception.ErrorCode;

public record ErrorResponse(
	String status,
	String message) {

	public static ErrorResponse from(ErrorCode errorCode) {
		return new ErrorResponse(errorCode.getStatus(), errorCode.getMessage());
	}

	public static ErrorResponse of(ErrorCode errorCode, String message) {
		return new ErrorResponse(errorCode.getStatus(), message);
	}
}
