package com.ssafy.e102.domain.place.dto.response;

import com.ssafy.e102.domain.place.entity.PlaceAccessibilityFeature;
import com.ssafy.e102.domain.place.type.AccessibilityFeatureType;

public record PlaceAccessibilityFeatureResponse(
	AccessibilityFeatureType featureType,
	boolean isAvailable) {

	public static PlaceAccessibilityFeatureResponse from(PlaceAccessibilityFeature feature) {
		return new PlaceAccessibilityFeatureResponse(feature.getFeatureType(), feature.isAvailable());
	}
}
