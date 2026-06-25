package com.ssafy.e102.global.security.jwt;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
	String secret,
	String issuer,
	Duration accessTokenTtl,
	Duration refreshTokenTtl,
	Duration signupTokenTtl) {

	public JwtProperties {
		Assert.hasText(secret, "JWT secret은 필수입니다.");
		Assert.hasText(issuer, "JWT issuer는 필수입니다.");
		Assert.notNull(accessTokenTtl, "JWT 액세스 토큰 만료 시간은 필수입니다.");
		Assert.notNull(refreshTokenTtl, "JWT 리프레시 토큰 만료 시간은 필수입니다.");
		Assert.notNull(signupTokenTtl, "JWT 회원가입 토큰 만료 시간은 필수입니다.");
		Assert.isTrue(!accessTokenTtl.isNegative() && !accessTokenTtl.isZero(),
			"JWT 액세스 토큰 만료 시간은 0보다 커야 합니다.");
		Assert.isTrue(!refreshTokenTtl.isNegative() && !refreshTokenTtl.isZero(),
			"JWT 리프레시 토큰 만료 시간은 0보다 커야 합니다.");
		Assert.isTrue(!signupTokenTtl.isNegative() && !signupTokenTtl.isZero(),
			"JWT 회원가입 토큰 만료 시간은 0보다 커야 합니다.");
	}
}
