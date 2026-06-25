package com.ssafy.e102.domain.place.entity;

import com.ssafy.e102.domain.place.exception.PlaceErrorCode;
import com.ssafy.e102.domain.place.exception.PlaceException;
import com.ssafy.e102.domain.place.type.AccessibilityFeatureType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "place_accessibility_features", uniqueConstraints = {
	@UniqueConstraint(name = "uk_place_accessibility_features_place_type", columnNames = {"place_id", "feature_type"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceAccessibilityFeature {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(nullable = false, updatable = false)
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "place_id", nullable = false)
	private Place place;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private AccessibilityFeatureType featureType;

	@Column(nullable = false)
	private boolean isAvailable;

	public static PlaceAccessibilityFeature create(
		Place place,
		AccessibilityFeatureType featureType,
		boolean isAvailable) {
		PlaceAccessibilityFeature feature = new PlaceAccessibilityFeature();
		feature.place = requirePlace(place);
		feature.featureType = requireFeatureType(featureType);
		feature.isAvailable = isAvailable;
		return feature;
	}

	private static Place requirePlace(Place place) {
		if (place == null) {
			throw invalidRequest("장소는 필수입니다.");
		}
		return place;
	}

	private static AccessibilityFeatureType requireFeatureType(AccessibilityFeatureType featureType) {
		if (featureType == null) {
			throw invalidRequest("접근성 속성 유형은 필수입니다.");
		}
		return featureType;
	}

	private static PlaceException invalidRequest(String message) {
		return new PlaceException(PlaceErrorCode.INVALID_PLACE_REQUEST, message);
	}
}
