package com.ssafy.e102.domain.report.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class HazardReportIdempotencyCleanupScheduler {

	private final HazardReportService hazardReportService;

	@Scheduled(cron = "${report.idempotency.cleanup-cron:0 0 * * * *}")
	void cleanupExpiredIdempotencyMetadata() {
		hazardReportService.cleanupExpiredIdempotencyMetadata();
	}
}
