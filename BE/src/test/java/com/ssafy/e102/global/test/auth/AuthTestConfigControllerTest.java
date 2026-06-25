package com.ssafy.e102.global.test.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AuthTestConfigControllerTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
		.withUserConfiguration(AuthTestConfigController.class);

	@Test
	@DisplayName("소셜 로그인 테스트 페이지용 public 설정을 반환한다")
	void getAuthTestConfig() {
		AuthTestConfigProperties properties = new AuthTestConfigProperties(
			true,
			"kakao-js-key",
			"naver-client-id",
			"google-client-id");
		AuthTestConfigController controller = new AuthTestConfigController(properties);

		AuthTestConfigResponse response = controller.getConfig();

		assertThat(response.kakaoJavaScriptKey()).isEqualTo("kakao-js-key");
		assertThat(response.naverClientId()).isEqualTo("naver-client-id");
		assertThat(response.googleClientId()).isEqualTo("google-client-id");
	}

	@Test
	@DisplayName("테스트 설정 API는 auth.test.enabled가 true일 때만 등록된다")
	void registerOnlyWhenEnabled() {
		contextRunner
			.withPropertyValues(
				"auth.test.enabled=true",
				"auth.test.kakao-javascript-key=kakao-js-key",
				"auth.test.naver-client-id=naver-client-id",
				"auth.test.google-client-id=google-client-id")
			.run(context -> assertThat(context).hasSingleBean(AuthTestConfigController.class));

		contextRunner
			.withPropertyValues("auth.test.enabled=false")
			.run(context -> assertThat(context).doesNotHaveBean(AuthTestConfigController.class));
	}
}
