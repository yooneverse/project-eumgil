package com.ssafy.e102.domain.place.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

import org.springframework.util.StringUtils;

public final class BookmarkTargetIdFactory {

	private static final String PREFIX = "tgt_";
	private static final int HASH_HEX_LENGTH = 16;

	private BookmarkTargetIdFactory() {}

	public static String fromInternalPlace(Long placeId) {
		return fromCanonical("internal:place|" + placeId);
	}

	public static String fromExternalPoi(
		String provider,
		String providerPlaceId,
		String name,
		double lat,
		double lng) {
		return fromCanonical(String.join(
			"|",
			normalizeProvider(provider),
			"poi",
			normalizeText(providerPlaceId),
			normalizeText(name),
			formatCoordinate(lat),
			formatCoordinate(lng)));
	}

	public static String fromExternalAddress(
		String provider,
		String name,
		double lat,
		double lng,
		String address) {
		return fromCanonical(String.join(
			"|",
			normalizeProvider(provider),
			"address",
			normalizeText(name),
			formatCoordinate(lat),
			formatCoordinate(lng),
			normalizeText(address)));
	}

	private static String fromCanonical(String canonicalInput) {
		byte[] hash = sha256(canonicalInput);
		return PREFIX + toHex(hash).substring(0, HASH_HEX_LENGTH);
	}

	private static byte[] sha256(String canonicalInput) {
		try {
			return MessageDigest.getInstance("SHA-256")
				.digest(canonicalInput.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
		}
	}

	private static String toHex(byte[] bytes) {
		StringBuilder builder = new StringBuilder(bytes.length * 2);
		for (byte value : bytes) {
			builder.append(Character.forDigit((value >> 4) & 0xF, 16));
			builder.append(Character.forDigit(value & 0xF, 16));
		}
		return builder.toString();
	}

	private static String normalizeProvider(String provider) {
		if (!StringUtils.hasText(provider)) {
			return "unknown";
		}
		return provider.trim().toLowerCase(Locale.ROOT);
	}

	private static String normalizeText(String value) {
		if (!StringUtils.hasText(value)) {
			return "";
		}
		return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
	}

	private static String formatCoordinate(double value) {
		return String.format(Locale.ROOT, "%.6f", value);
	}
}
