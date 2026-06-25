package com.ssafy.e102.domain.route.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.ssafy.e102.global.exception.BusinessException;

class RouteExceptionTest {

	@Test
	@DisplayName("경로 예외는 공통 비즈니스 예외 계약을 따른다")
	void routeExceptionExtendsBusinessException() {
		RouteException exception = new RouteException(RouteErrorCode.ROUTE_NOT_FOUND);

		assertThat(exception).isInstanceOf(BusinessException.class);
		assertThat(exception.getErrorCode()).isEqualTo(RouteErrorCode.ROUTE_NOT_FOUND);
		assertThat(exception.getMessage()).isEqualTo("탐색 가능한 경로가 없습니다.");
	}

	@Test
	@DisplayName("경로 에러 코드는 API 계약의 상태와 메시지를 가진다")
	void routeErrorCodeHasResponseFields() {
		assertThat(RouteErrorCode.ROUTE_NOT_FOUND.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(RouteErrorCode.ROUTE_NOT_FOUND.getStatus()).isEqualTo("RT4040");
		assertThat(RouteErrorCode.ROUTE_NOT_FOUND.getMessage()).isEqualTo("탐색 가능한 경로가 없습니다.");
		assertThat(RouteErrorCode.EXTERNAL_ROUTE_API_FAILED.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
		assertThat(RouteErrorCode.EXTERNAL_ROUTE_API_FAILED.getStatus()).isEqualTo("EX5020");
		assertThat(RouteErrorCode.EXTERNAL_ROUTE_API_TIMEOUT.getHttpStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
		assertThat(RouteErrorCode.EXTERNAL_ROUTE_API_TIMEOUT.getStatus()).isEqualTo("EX5040");
		assertThat(RouteErrorCode.ROUTE_SESSION_NOT_COMPLETED.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(RouteErrorCode.ROUTE_SESSION_NOT_COMPLETED.getStatus()).isEqualTo("RT4092");
		assertThat(RouteErrorCode.ROUTE_SESSION_NOT_COMPLETED.getMessage()).isEqualTo("종료된 안내 세션만 평가할 수 있습니다.");
	}
}
