package com.ssafy.e102.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.data.auditing.DateTimeProvider;

class JpaAuditingConfigTest {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	@Test
	void serviceClockUsesSeoulZone() {
		JpaAuditingConfig config = new JpaAuditingConfig();

		assertThat(config.serviceClock().getZone()).isEqualTo(SEOUL_ZONE);
	}

	@Test
	void auditingDateTimeProviderUsesSeoulLocalTime() {
		JpaAuditingConfig config = new JpaAuditingConfig();
		Clock fixedSeoulClock = Clock.fixed(Instant.parse("2026-05-13T22:46:00Z"), SEOUL_ZONE);

		DateTimeProvider provider = config.auditingDateTimeProvider(fixedSeoulClock);

		assertThat(provider.getNow())
			.hasValueSatisfying(value -> assertThat(LocalDateTime.from(value))
				.isEqualTo(LocalDateTime.of(2026, 5, 14, 7, 46)));
	}
}
