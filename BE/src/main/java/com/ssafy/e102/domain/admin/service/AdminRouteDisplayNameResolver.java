package com.ssafy.e102.domain.admin.service;

import java.util.List;

import com.ssafy.e102.domain.admin.dto.response.AdminDashboardBottleneckResponse.GeoPointResponse;
import com.ssafy.e102.domain.place.repository.PlaceRepository;

class AdminRouteDisplayNameResolver {

	private static final int PLACE_NAME_RADIUS_METERS = 120;

	private final PlaceRepository placeRepository;

	AdminRouteDisplayNameResolver(PlaceRepository placeRepository) {
		this.placeRepository = placeRepository;
	}

	String resolve(
		String rawTitle,
		List<GeoPointResponse> points,
		String startGu,
		String startDong,
		String endGu,
		String endDong,
		String fallbackName) {
		String normalizedTitle = normalize(rawTitle);
		if (hasText(normalizedTitle) && !isGenericRouteTitle(normalizedTitle)) {
			return normalizedTitle;
		}

		String nearbyPlaceName = nearbyPlaceName(points);
		if (hasText(nearbyPlaceName)) {
			return nearbyPlaceName.strip() + " 인근";
		}

		String adminAreaName = adminAreaName(startGu, startDong, endGu, endDong);
		if (hasText(adminAreaName)) {
			return adminAreaName;
		}

		if (hasText(normalizedTitle)) {
			return normalizedTitle;
		}

		return fallbackName;
	}

	private String nearbyPlaceName(List<GeoPointResponse> points) {
		if (points == null || points.isEmpty()) {
			return null;
		}
		GeoPointResponse center = points.get(points.size() / 2);
		return placeRepository.findNearestPlaceName(center.lat(), center.lng(), PLACE_NAME_RADIUS_METERS)
			.orElse(null);
	}

	private String adminAreaName(String startGu, String startDong, String endGu, String endDong) {
		if (!hasText(startGu) || !hasText(startDong) || !hasText(endGu) || !hasText(endDong)) {
			return null;
		}
		if (startGu.equals(endGu) && startDong.equals(endDong)) {
			return startGu + " " + startDong + " 순환축";
		}
		if (startGu.equals(endGu)) {
			return startDong + "-" + endDong + " 이동축";
		}
		return startGu + " " + startDong + "-" + endGu + " " + endDong + " 이동축";
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private String normalize(String value) {
		return hasText(value) ? value.strip() : null;
	}

	private boolean isGenericRouteTitle(String value) {
		return switch (value) {
			case "안전 경로", "추천 경로", "최단 경로", "최소 환승 경로", "최소 도보 경로",
				"Safe Route", "Recommended Route", "Shortest Route", "Least Transfer Route", "Least Walk Route" -> true;
			default -> false;
		};
	}
}
