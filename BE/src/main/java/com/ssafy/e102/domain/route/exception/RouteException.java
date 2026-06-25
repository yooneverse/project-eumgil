package com.ssafy.e102.domain.route.exception;

import com.ssafy.e102.global.exception.BusinessException;

/**
 * route 도메인 service와 external client가 던지는 비즈니스 예외다.
 *
 * <p>RouteErrorCode를 감싸 controller 밖 공통 예외 처리기로 넘기며, 외부 API 원본 예외는 cause로만 보관한다.
 */
public class RouteException extends BusinessException {

	public RouteException(RouteErrorCode errorCode) {
		super(errorCode);
	}

	public RouteException(RouteErrorCode errorCode, String message) {
		super(errorCode, message);
	}

	public RouteException(RouteErrorCode errorCode, String message, Throwable cause) {
		super(errorCode, message, cause);
	}
}
