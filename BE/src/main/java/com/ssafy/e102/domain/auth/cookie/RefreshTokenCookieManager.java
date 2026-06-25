package com.ssafy.e102.domain.auth.cookie;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.ssafy.e102.global.security.jwt.JwtProperties;

@Component
public class RefreshTokenCookieManager {

	private final JwtProperties jwtProperties;
	private final String cookieName;
	private final String cookiePath;
	private final String sameSite;
	private final boolean secure;

	public RefreshTokenCookieManager(
		JwtProperties jwtProperties,
		@Value("${auth.refresh-cookie.name:refreshToken}")
		String cookieName,
		@Value("${auth.refresh-cookie.path:/auth}")
		String cookiePath,
		@Value("${auth.refresh-cookie.same-site:Lax}")
		String sameSite,
		@Value("${auth.refresh-cookie.secure:true}")
		boolean secure) {
		this.jwtProperties = jwtProperties;
		this.cookieName = cookieName;
		this.cookiePath = cookiePath;
		this.sameSite = sameSite;
		this.secure = secure;
	}

	public String cookieName() {
		return cookieName;
	}

	public ResponseCookie create(String refreshToken) {
		return ResponseCookie.from(cookieName, refreshToken)
			.httpOnly(true)
			.secure(secure)
			.sameSite(sameSite)
			.path(cookiePath)
			.maxAge(jwtProperties.refreshTokenTtl())
			.build();
	}

	public ResponseCookie clear() {
		return ResponseCookie.from(cookieName, "")
			.httpOnly(true)
			.secure(secure)
			.sameSite(sameSite)
			.path(cookiePath)
			.maxAge(0)
			.build();
	}
}
