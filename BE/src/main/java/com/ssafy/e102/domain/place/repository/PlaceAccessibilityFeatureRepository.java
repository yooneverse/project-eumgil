package com.ssafy.e102.domain.place.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ssafy.e102.domain.place.entity.PlaceAccessibilityFeature;

public interface PlaceAccessibilityFeatureRepository extends JpaRepository<PlaceAccessibilityFeature, Integer> {

	List<PlaceAccessibilityFeature> findAllByPlace_PlaceId(Long placeId);

	void deleteAllByPlace_PlaceId(Long placeId);
}
