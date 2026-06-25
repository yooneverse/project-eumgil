package com.ssafy.e102.domain.auth.social.config;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(SocialProviderProperties.class)
public class SocialProviderClientConfig {

	@Bean
	public RestTemplate socialProviderRestTemplate(RestTemplateBuilder builder) {
		// 외부 소셜 API 장애가 내부 요청 스레드를 오래 점유하지 않도록 짧게 제한한다.
		return builder.connectTimeout(Duration.ofSeconds(3))
			.readTimeout(Duration.ofSeconds(3))
			.build();
	}

	@Bean
	public SocialProviderProperties.Kakao kakaoSocialProviderProperties(SocialProviderProperties properties) {
		return properties.kakao();
	}

	@Bean
	public SocialProviderProperties.Naver naverSocialProviderProperties(SocialProviderProperties properties) {
		return properties.naver();
	}

	@Bean
	public SocialProviderProperties.Google googleSocialProviderProperties(SocialProviderProperties properties) {
		return properties.google();
	}
}
