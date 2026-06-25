package com.ssafy.e102.global.test.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "인증 테스트 설정", description = "로컬/테스트 환경에서 소셜 로그인 테스트에 필요한 클라이언트 설정 조회 API")
@RestController
@RequestMapping("/auth/test-config")
@EnableConfigurationProperties(AuthTestConfigProperties.class)
@ConditionalOnProperty(prefix = "auth.test", name = "enabled", havingValue = "true")
public class AuthTestConfigController {

	private final AuthTestConfigProperties properties;

	public AuthTestConfigController(AuthTestConfigProperties properties) {
		this.properties = properties;
	}

	@Operation(summary = "소셜 로그인 테스트 설정 조회", description = "로컬/테스트 환경에서 소셜 로그인 테스트에 필요한 카카오, 네이버, 구글 클라이언트 설정을 조회한다.")
	@GetMapping
	public AuthTestConfigResponse getConfig() {
		return new AuthTestConfigResponse(
			properties.kakaoJavaScriptKey(),
			properties.naverClientId(),
			properties.googleClientId());
	}
}
