package com.ssafy.e102.global.external.graphhopper;

import org.springframework.util.StringUtils;

/**
 * 한 번의 GraphHopper 호출에서 사용할 active endpoint와 previous fallback endpoint다.
 */
public record GraphHopperEndpointSelection(
	String activeBaseUrl,
	String previousBaseUrl,
	String activeSlot,
	String previousSlot) {

	public GraphHopperEndpointSelection {
		if (!StringUtils.hasText(activeBaseUrl)) {
			throw new IllegalArgumentException("active GraphHopper endpoint is required");
		}
		previousBaseUrl = StringUtils.hasText(previousBaseUrl) ? previousBaseUrl : null;
		activeSlot = StringUtils.hasText(activeSlot) ? activeSlot : "fallback";
		previousSlot = StringUtils.hasText(previousSlot) ? previousSlot : null;
	}

	public static GraphHopperEndpointSelection fallback(String baseUrl) {
		return new GraphHopperEndpointSelection(baseUrl, null, "fallback", null);
	}

	public boolean hasPrevious() {
		return StringUtils.hasText(previousBaseUrl);
	}
}
