package com.ssafy.e102.domain.report.storage;

import java.time.Duration;

public interface ReportImagePresigner {

	ReportImagePresignedUrl createPutObjectPresignedUrl(
		String objectKey,
		String contentType,
		long contentLength,
		Duration signatureDuration);

	ReportImagePresignedUrl createGetObjectPresignedUrl(
		String objectKey,
		Duration signatureDuration);
}
