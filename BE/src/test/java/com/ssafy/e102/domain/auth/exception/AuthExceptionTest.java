package com.ssafy.e102.domain.auth.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.ssafy.e102.global.exception.BusinessException;

class AuthExceptionTest {

	@Test
	@DisplayName("인증 예외는 공통 비즈니스 예외 계약을 따른다")
	void authExceptionExtendsBusinessException() {
		AuthException exception = new AuthException(AuthErrorCode.INVALID_SOCIAL_TOKEN);

		assertThat(exception).isInstanceOf(BusinessException.class);
		assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_SOCIAL_TOKEN);
		assertThat(exception.getMessage()).isEqualTo("인증에 실패했습니다.");
	}

	@Test
	@DisplayName("인증 에러 코드는 응답 상태와 메시지를 가진다")
	void authErrorCodeHasResponseFields() {
		AuthErrorCode errorCode = AuthErrorCode.SOCIAL_PROVIDER_API_FAILED;

		assertThat(errorCode.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
		assertThat(errorCode.getStatus()).isEqualTo("A5020");
		assertThat(errorCode.getMessage()).isEqualTo("소셜 로그인 연동에 실패했습니다.");
	}
}
