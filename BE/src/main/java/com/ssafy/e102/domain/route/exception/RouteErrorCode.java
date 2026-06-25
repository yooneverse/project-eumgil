package com.ssafy.e102.domain.route.exception;

import org.springframework.http.HttpStatus;

import com.ssafy.e102.global.exception.ErrorCode;

/**
 * route 도메인에서 API 명세와 맞춰 반환하는 에러 코드다.
 *
 * <p>service와 external client에서 발생한 실패를 GlobalExceptionHandler가 공통 에러 응답으로 변환할 때 사용한다.
 */
public enum RouteErrorCode implements ErrorCode {

	INVALID_ROUTE_REQUEST(HttpStatus.BAD_REQUEST, "RT4000", "경로 요청값이 올바르지 않습니다."),
	INVALID_REROUTE_REQUEST(HttpStatus.BAD_REQUEST, "RT4001", "재탐색 요청값이 올바르지 않습니다."),
	INVALID_ROUTE_SELECT_REQUEST(HttpStatus.BAD_REQUEST, "RT4002", "경로 선택 요청값이 올바르지 않습니다."),
	OUT_OF_SERVICE_AREA(HttpStatus.BAD_REQUEST, "RT4003", "부산광역시 안의 위치를 선택해 주세요."),
	START_END_TOO_CLOSE(HttpStatus.BAD_REQUEST, "RT4004", "출발지와 도착지를 다르게 선택해 주세요."),
	INVALID_CURRENT_POINT(HttpStatus.BAD_REQUEST, "RT4005", "현재 위치값이 올바르지 않습니다."),
	INVALID_TRANSIT_REFRESH_REQUEST(HttpStatus.BAD_REQUEST, "PT4000", "도착정보 갱신 요청값이 올바르지 않습니다."),
	INVALID_ROUTE_RATING_REQUEST(HttpStatus.BAD_REQUEST, "RR4000", "경로 평가 요청값이 올바르지 않습니다."),
	ROUTE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "A4030", "접근할 수 없는 경로입니다."),
	ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "RT4040", "탐색 가능한 경로가 없습니다."),
	ROUTE_SEARCH_EXPIRED(HttpStatus.NOT_FOUND, "RT4041", "검색 결과가 만료되었습니다."),
	ROUTE_CANDIDATE_NOT_FOUND(HttpStatus.NOT_FOUND, "RT4042", "선택한 경로 후보를 찾을 수 없습니다."),
	ROUTE_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "RT4043", "선택한 경로 정보를 찾을 수 없습니다."),
	ROUTE_SELECT_CONFLICT(HttpStatus.CONFLICT, "RT4090", "선택할 수 없는 경로입니다."),
	ROUTE_TOO_FAR_FOR_REROUTE(HttpStatus.CONFLICT, "RT4091", "현재 위치가 기존 경로에서 너무 멀리 벗어났습니다."),
	ROUTE_SESSION_NOT_COMPLETED(HttpStatus.CONFLICT, "RT4092", "종료된 안내 세션만 평가할 수 있습니다."),
	NOT_TRANSIT_LEG(HttpStatus.CONFLICT, "PT4090", "대중교통 구간이 아닙니다."),
	EXTERNAL_ROUTE_API_FAILED(HttpStatus.BAD_GATEWAY, "EX5020", "외부 경로 정보를 불러오지 못했습니다."),
	EXTERNAL_ROUTE_API_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "EX5040", "외부 경로 정보 응답이 지연되고 있습니다.");

	private final HttpStatus httpStatus;
	private final String status;
	private final String message;

	RouteErrorCode(HttpStatus httpStatus, String status, String message) {
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
