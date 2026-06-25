package com.ssafy.e102.domain.report.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import com.ssafy.e102.domain.report.dto.request.CreateHazardReportRequest;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class HazardReportIdempotencyRequestHash {

	private static final String HASH_ALGORITHM = "SHA-256";

	static String from(CreateHazardReportRequest request) {
		return sha256(canonicalize(request, true));
	}

	static boolean matchesStoredHash(String storedHash, CreateHazardReportRequest request) {
		if (storedHash == null || storedHash.isBlank()) {
			return false;
		}
		if (storedHash.equals(from(request))) {
			return true;
		}
		return storedHash.equals(legacyFrom(request));
	}

	static String legacyFrom(CreateHazardReportRequest request) {
		return sha256(canonicalize(request, false));
	}

	private static String canonicalize(CreateHazardReportRequest request, boolean includeThumbnailObjectKeys) {
		StringBuilder canonical = new StringBuilder();
		appendPart(canonical, request.reportType() == null ? null : request.reportType().name());
		appendPart(canonical, normalizeDescription(request.description()));
		GeoPointRequest reportPoint = request.reportPoint();
		appendPart(canonical, reportPoint == null || reportPoint.lat() == null ? null : reportPoint.lat().toString());
		appendPart(canonical, reportPoint == null || reportPoint.lng() == null ? null : reportPoint.lng().toString());
		List<String> imageObjectKeys = request.imageObjectKeys() == null ? List.of() : request.imageObjectKeys();
		canonical.append(imageObjectKeys.size()).append('|');
		for (String imageObjectKey : imageObjectKeys) {
			appendPart(canonical, imageObjectKey);
		}
		if (includeThumbnailObjectKeys) {
			List<String> thumbnailObjectKeys = request.thumbnailObjectKeys() == null ? List.of() : request.thumbnailObjectKeys();
			canonical.append(thumbnailObjectKeys.size()).append('|');
			for (String thumbnailObjectKey : thumbnailObjectKeys) {
				appendPart(canonical, thumbnailObjectKey);
			}
		}
		return canonical.toString();
	}

	private static String normalizeDescription(String description) {
		if (description == null || description.isBlank()) {
			return null;
		}
		return description.trim();
	}

	private static void appendPart(StringBuilder canonical, String value) {
		if (value == null) {
			canonical.append("-1:|");
			return;
		}
		canonical.append(value.length()).append(':').append(value).append('|');
	}

	private static String sha256(String value) {
		try {
			byte[] digest = MessageDigest.getInstance(HASH_ALGORITHM)
				.digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}
}
