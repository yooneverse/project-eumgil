package com.ssafy.e102.domain.route.entity;

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
@Table(name = "subway_station_accessibility_features", uniqueConstraints = {
	@UniqueConstraint(name = "uk_subway_station_accessibility_features_station_type", columnNames = {
		"subway_station_id", "feature_type"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubwayStationAccessibilityFeature {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(nullable = false, updatable = false)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "subway_station_id", nullable = false)
	private SubwayStation subwayStation;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private AccessibilityFeatureType featureType;

	@Column(nullable = false)
	private boolean isAvailable;

	public static SubwayStationAccessibilityFeature create(
		SubwayStation subwayStation,
		AccessibilityFeatureType featureType,
		boolean isAvailable) {
		SubwayStationAccessibilityFeature feature = new SubwayStationAccessibilityFeature();
		feature.subwayStation = requireSubwayStation(subwayStation);
		feature.featureType = requireFeatureType(featureType);
		feature.isAvailable = isAvailable;
		return feature;
	}

	public void updateAvailability(boolean isAvailable) {
		this.isAvailable = isAvailable;
	}

	private static SubwayStation requireSubwayStation(SubwayStation subwayStation) {
		if (subwayStation == null) {
			throw invalidRequest("지하철역은 필수입니다.");
		}
		return subwayStation;
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
