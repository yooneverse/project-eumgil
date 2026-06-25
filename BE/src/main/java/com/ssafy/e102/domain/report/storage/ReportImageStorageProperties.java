package com.ssafy.e102.domain.report.storage;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "report.image-storage")
public record ReportImageStorageProperties(
	String bucket,
	String region,
	String endpoint,
	String accessKey,
	String secretKey,
	String keyPrefix,
	Duration presignTtl,
	DataSize maxContentLength,
	List<String> allowedContentTypes,
	Boolean pathStyleAccessEnabled) {

	private static final String DEFAULT_BUCKET = "e102-report-images";
	private static final String DEFAULT_REGION = "ap-northeast-2";
	private static final String DEFAULT_KEY_PREFIX = "hazard-reports";
	private static final Duration DEFAULT_PRESIGN_TTL = Duration.ofMinutes(10);
	private static final DataSize DEFAULT_MAX_CONTENT_LENGTH = DataSize.ofMegabytes(10);
	private static final List<String> DEFAULT_ALLOWED_CONTENT_TYPES = List.of(
		"image/jpeg",
		"image/png",
		"image/webp",
		"image/heic",
		"image/heif");

	public ReportImageStorageProperties {
		bucket = textOrDefault(bucket, DEFAULT_BUCKET);
		region = textOrDefault(region, DEFAULT_REGION);
		endpoint = normalizeNullableBaseUrl(endpoint);
		accessKey = trimToNull(accessKey);
		secretKey = trimToNull(secretKey);
		keyPrefix = normalizeKeyPrefix(textOrDefault(keyPrefix, DEFAULT_KEY_PREFIX));
		presignTtl = presignTtl == null ? DEFAULT_PRESIGN_TTL : presignTtl;
		maxContentLength = maxContentLength == null ? DEFAULT_MAX_CONTENT_LENGTH : maxContentLength;
		allowedContentTypes = normalizeContentTypes(allowedContentTypes);
		pathStyleAccessEnabled = pathStyleAccessEnabled == null
			? StringUtils.hasText(endpoint)
			: pathStyleAccessEnabled;
	}

	public boolean hasStaticCredentials() {
		return StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey);
	}

	private static List<String> normalizeContentTypes(List<String> contentTypes) {
		List<String> source = contentTypes == null || contentTypes.isEmpty()
			? DEFAULT_ALLOWED_CONTENT_TYPES
			: contentTypes;
		return source.stream()
			.filter(StringUtils::hasText)
			.map(value -> value.trim().toLowerCase(Locale.ROOT))
			.distinct()
			.toList();
	}

	private static String normalizeNullableBaseUrl(String value) {
		String trimmed = trimToNull(value);
		if (trimmed == null) {
			return null;
		}
		while (trimmed.endsWith("/")) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed;
	}

	private static String normalizeKeyPrefix(String value) {
		String normalized = value.trim();
		while (normalized.startsWith("/")) {
			normalized = normalized.substring(1);
		}
		while (normalized.endsWith("/")) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		return StringUtils.hasText(normalized) ? normalized : DEFAULT_KEY_PREFIX;
	}

	private static String textOrDefault(String value, String defaultValue) {
		return StringUtils.hasText(value) ? value.trim() : defaultValue;
	}

	private static String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}
}
