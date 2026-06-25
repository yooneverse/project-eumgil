package com.ssafy.e102.domain.place.entity;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Point;

import com.ssafy.e102.domain.place.exception.PlaceErrorCode;
import com.ssafy.e102.domain.place.exception.PlaceException;
import com.ssafy.e102.domain.place.type.PlaceCategory;
import com.ssafy.e102.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "places", uniqueConstraints = {
	@UniqueConstraint(name = "uk_places_provider_place_id", columnNames = "provider_place_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(nullable = false, updatable = false)
	private Long placeId;

	@Column(nullable = false, length = 255)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private PlaceCategory category;

	@Column(length = 255)
	private String address;

	@Column(nullable = false, columnDefinition = "geometry(Point, 4326)")
	private Point point;

	@Column(length = 100)
	private String providerPlaceId;

	@OneToMany(mappedBy = "place", fetch = FetchType.LAZY)
	private List<PlaceAccessibilityFeature> accessibilityFeatures = new ArrayList<>();

	public void updateBasicInfo(
		String name,
		PlaceCategory category,
		String address,
		Point point,
		String providerPlaceId) {
		this.name = normalizeName(valueOrDefault(name, this.name));
		this.category = valueOrDefault(category, this.category);
		this.address = normalizeNullableText(valueOrDefault(address, this.address));
		this.point = requirePoint(valueOrDefault(point, this.point));
		this.providerPlaceId = normalizeNullableText(valueOrDefault(providerPlaceId, this.providerPlaceId));
	}

	private static String normalizeName(String name) {
		if (name == null || name.isBlank()) {
			throw invalidRequest("장소명은 필수입니다.");
		}
		return name.trim();
	}

	private static String normalizeNullableText(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private static Point requirePoint(Point point) {
		if (point == null) {
			throw invalidRequest("장소 좌표는 필수입니다.");
		}
		return point;
	}

	private static <T> T valueOrDefault(T value, T defaultValue) {
		if (value == null) {
			return defaultValue;
		}
		return value;
	}

	private static PlaceException invalidRequest(String message) {
		return new PlaceException(PlaceErrorCode.INVALID_PLACE_REQUEST, message);
	}
}
