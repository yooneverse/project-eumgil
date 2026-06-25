package com.ssafy.e102.domain.bookmark.exception;

import com.ssafy.e102.global.exception.BusinessException;

public class FavoriteRouteException extends BusinessException {

	public FavoriteRouteException(FavoriteRouteErrorCode errorCode) {
		super(errorCode);
	}

	public FavoriteRouteException(FavoriteRouteErrorCode errorCode, String message) {
		super(errorCode, message);
	}

	public FavoriteRouteException(FavoriteRouteErrorCode errorCode, String message, Throwable cause) {
		super(errorCode, message, cause);
	}
}
