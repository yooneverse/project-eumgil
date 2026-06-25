package com.ssafy.e102.domain.auth.exception;

import com.ssafy.e102.global.exception.BusinessException;

public class AuthException extends BusinessException {

	public AuthException(AuthErrorCode errorCode) {
		super(errorCode);
	}

	public AuthException(AuthErrorCode errorCode, String message) {
		super(errorCode, message);
	}

	public AuthException(AuthErrorCode errorCode, String message, Throwable cause) {
		super(errorCode, message, cause);
	}
}
