package com.ssafy.e102.domain.place.exception;

import com.ssafy.e102.global.exception.BusinessException;

public class PlaceException extends BusinessException {

	public PlaceException(PlaceErrorCode errorCode) {
		super(errorCode);
	}

	public PlaceException(PlaceErrorCode errorCode, String message) {
		super(errorCode, message);
	}

	public PlaceException(PlaceErrorCode errorCode, String message, Throwable cause) {
		super(errorCode, message, cause);
	}

	public PlaceException(PlaceErrorCode errorCode, Throwable cause) {
		super(errorCode, errorCode.getMessage(), cause);
	}
}
