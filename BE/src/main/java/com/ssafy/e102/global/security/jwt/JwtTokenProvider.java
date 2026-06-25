package com.ssafy.e102.global.security.jwt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ssafy.e102.domain.auth.token.SignupTokenPayload;
import com.ssafy.e102.domain.user.type.SocialProvider;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtTokenProvider {

	private static final String TOKEN_TYPE_CLAIM = "tokenType";
	private static final String SOCIAL_PROVIDER_CLAIM = "socialProvider";
	private static final String ACCESS_TOKEN_TYPE = "ACCESS";
	private static final String REFRESH_TOKEN_TYPE = "REFRESH";
	private static final String SIGNUP_TOKEN_TYPE = "SIGNUP";

	private final JwtProperties properties;
	private final Clock clock;
	private final SecretKey signingKey;

	@Autowired
	public JwtTokenProvider(JwtProperties properties) {
		this(properties, Clock.systemUTC());
	}

	JwtTokenProvider(JwtProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
		this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
	}

	public String createAccessToken(UUID userId) {
		return createUserToken(userId, ACCESS_TOKEN_TYPE, properties.accessTokenTtl());
	}

	public String createRefreshToken(UUID userId) {
		return createUserToken(userId, REFRESH_TOKEN_TYPE, properties.refreshTokenTtl());
	}

	public String createSignupToken(SocialProvider socialProvider, String socialProviderUserId) {
		Instant now = clock.instant();
		return Jwts.builder()
			.issuer(properties.issuer())
			.subject(socialProviderUserId)
			.claim(TOKEN_TYPE_CLAIM, SIGNUP_TOKEN_TYPE)
			.claim(SOCIAL_PROVIDER_CLAIM, socialProvider.name())
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plus(properties.signupTokenTtl())))
			.signWith(signingKey)
			.compact();
	}

	public UUID getAccessTokenSubject(String token) {
		return UUID.fromString(getSubject(token, ACCESS_TOKEN_TYPE));
	}

	public Optional<Duration> getAccessTokenRemainingTtl(String token) {
		try {
			Claims claims = parseClaims(token);
			validateTokenType(claims, ACCESS_TOKEN_TYPE);
			Duration remainingTtl = Duration.between(clock.instant(), claims.getExpiration().toInstant());
			if (remainingTtl.isNegative() || remainingTtl.isZero()) {
				return Optional.empty();
			}
			return Optional.of(remainingTtl);
		} catch (JwtTokenException exception) {
			return Optional.empty();
		}
	}

	public UUID getRefreshTokenSubject(String token) {
		return UUID.fromString(getSubject(token, REFRESH_TOKEN_TYPE));
	}

	public SignupTokenPayload getSignupTokenPayload(String token) {
		Claims claims = parseClaims(token);
		validateTokenType(claims, SIGNUP_TOKEN_TYPE);

		return new SignupTokenPayload(
			SocialProvider.valueOf(claims.get(SOCIAL_PROVIDER_CLAIM, String.class)),
			claims.getSubject());
	}

	private String createUserToken(UUID userId, String tokenType, Duration ttl) {
		Instant now = clock.instant();
		return Jwts.builder()
			.issuer(properties.issuer())
			.subject(userId.toString())
			.claim(TOKEN_TYPE_CLAIM, tokenType)
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plus(ttl)))
			.signWith(signingKey)
			.compact();
	}

	private String getSubject(String token, String expectedTokenType) {
		Claims claims = parseClaims(token);
		validateTokenType(claims, expectedTokenType);
		return claims.getSubject();
	}

	private Claims parseClaims(String token) {
		try {
			return Jwts.parser()
				.verifyWith(signingKey)
				.clock(() -> Date.from(clock.instant()))
				.requireIssuer(properties.issuer())
				.build()
				.parseSignedClaims(token)
				.getPayload();
		} catch (ExpiredJwtException exception) {
			throw new JwtTokenException("토큰이 만료되었습니다.");
		} catch (JwtException | IllegalArgumentException exception) {
			throw new JwtTokenException("토큰이 유효하지 않습니다.");
		}
	}

	private void validateTokenType(Claims claims, String expectedTokenType) {
		if (!expectedTokenType.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
			throw new JwtTokenException("토큰 유형이 올바르지 않습니다.");
		}
	}
}
