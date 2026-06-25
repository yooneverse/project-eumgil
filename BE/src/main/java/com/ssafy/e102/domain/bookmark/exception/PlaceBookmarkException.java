package com.ssafy.e102.domain.bookmark.exception;

import com.ssafy.e102.global.exception.BusinessException;

public class PlaceBookmarkException extends BusinessException {

	public PlaceBookmarkException(PlaceBookmarkErrorCode errorCode) {
		super(errorCode);
	}

	public PlaceBookmarkException(PlaceBookmarkErrorCode errorCode, String message) {
		super(errorCode, message);
	}
}
