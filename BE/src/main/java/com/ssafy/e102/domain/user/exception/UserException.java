package com.ssafy.e102.domain.user.exception;

import com.ssafy.e102.global.exception.BusinessException;

public class UserException extends BusinessException {

	public UserException(UserErrorCode errorCode) {
		super(errorCode);
	}

	public UserException(UserErrorCode errorCode, String message) {
		super(errorCode, message);
	}

	public UserException(UserErrorCode errorCode, String message, Throwable cause) {
		super(errorCode, message, cause);
	}
}
