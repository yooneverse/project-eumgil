package com.ssafy.e102.domain.bookmark.exception;

import org.springframework.http.HttpStatus;

import com.ssafy.e102.global.exception.ErrorCode;

public enum PlaceBookmarkErrorCode implements ErrorCode {

	INVALID_PLACE_BOOKMARK_REQUEST(HttpStatus.BAD_REQUEST, "BM4000", "북마크 요청값이 올바르지 않습니다."),
	INVALID_PLACE_BOOKMARK_DELETE_REQUEST(HttpStatus.BAD_REQUEST, "BM4001", "북마크 해제 요청값이 올바르지 않습니다."),
	PLACE_BOOKMARK_PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "BM4040", "장소를 찾을 수 없습니다."),
	PLACE_BOOKMARK_NOT_FOUND(HttpStatus.NOT_FOUND, "BM4041", "북마크 내역을 찾을 수 없습니다."),
	PLACE_BOOKMARK_ALREADY_EXISTS(HttpStatus.CONFLICT, "BM4090", "이미 북마크한 장소입니다.");

	private final HttpStatus httpStatus;
	private final String status;
	private final String message;

	PlaceBookmarkErrorCode(HttpStatus httpStatus, String status, String message) {
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
