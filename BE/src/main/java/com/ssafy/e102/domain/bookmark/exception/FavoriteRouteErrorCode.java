package com.ssafy.e102.domain.bookmark.exception;

import org.springframework.http.HttpStatus;

import com.ssafy.e102.global.exception.ErrorCode;

public enum FavoriteRouteErrorCode implements ErrorCode {

	INVALID_FAVORITE_ROUTE_REQUEST(HttpStatus.BAD_REQUEST, "FR4000", "경로 북마크 요청값이 올바르지 않습니다."),
	INVALID_FAVORITE_ROUTE_UPDATE_REQUEST(HttpStatus.BAD_REQUEST, "FR4001", "경로 북마크 수정 요청값이 올바르지 않습니다."),
	FAVORITE_ROUTE_FORBIDDEN(HttpStatus.FORBIDDEN, "FR4030", "경로 북마크에 대한 권한이 없습니다."),
	FAVORITE_ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "FR4040", "경로 북마크를 찾을 수 없습니다."),
	ROUTE_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "FR4041", "선택한 경로 정보를 찾을 수 없습니다.");

	private final HttpStatus httpStatus;
	private final String status;
	private final String message;

	FavoriteRouteErrorCode(HttpStatus httpStatus, String status, String message) {
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
