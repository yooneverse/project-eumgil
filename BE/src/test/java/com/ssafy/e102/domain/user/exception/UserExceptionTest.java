package com.ssafy.e102.domain.user.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.ssafy.e102.global.exception.BusinessException;

class UserExceptionTest {

	@Test
	@DisplayName("사용자 예외는 공통 비즈니스 예외 계약을 따른다")
	void userExceptionExtendsBusinessException() {
		UserException exception = new UserException(UserErrorCode.USER_NOT_FOUND);

		assertThat(exception).isInstanceOf(BusinessException.class);
		assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
		assertThat(exception.getMessage()).isEqualTo("사용자를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("사용자 에러 코드는 응답 상태와 메시지를 가진다")
	void userErrorCodeHasResponseFields() {
		UserErrorCode errorCode = UserErrorCode.ALREADY_REGISTERED_USER;

		assertThat(errorCode.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(errorCode.getStatus()).isEqualTo("U4090");
		assertThat(errorCode.getMessage()).isEqualTo("이미 가입된 사용자입니다.");
	}
}
