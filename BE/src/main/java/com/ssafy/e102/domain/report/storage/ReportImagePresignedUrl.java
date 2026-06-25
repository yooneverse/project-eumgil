package com.ssafy.e102.domain.report.storage;

import java.time.Instant;

public record ReportImagePresignedUrl(
	String url,
	Instant expiresAt) {
}
