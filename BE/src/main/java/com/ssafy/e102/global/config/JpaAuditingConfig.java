package com.ssafy.e102.global.config;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
@Configuration
public class JpaAuditingConfig {

	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

	@Bean
	public Clock serviceClock() {
		return Clock.system(SERVICE_ZONE);
	}

	@Bean
	public DateTimeProvider auditingDateTimeProvider(Clock serviceClock) {
		return () -> Optional.of(LocalDateTime.now(serviceClock));
	}
}
