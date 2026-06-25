package com.ssafy.e102.domain.route.entity;

import java.math.BigDecimal;

import org.locationtech.jts.geom.Geometry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * CSV에서 정규화한 접근성 원천 feature 저장소다.
 *
 * <p>{@code segment_features}가 현재 edge에 매칭된 라우팅/안내용 결과라면, 이 테이블은 이후 관리자 segment
 * 추가 시 주변 원천 feature를 다시 찾기 위한 영속 source layer다.
 */
@Getter
@Entity
@Table(name = "source_features")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SourceFeature {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "source_feature_id", nullable = false, updatable = false)
	private Long sourceFeatureId;

	@Column(name = "feature_type", nullable = false, length = 50)
	private String featureType;

	@Column(name = "geom", nullable = false, columnDefinition = "geometry(Geometry, 4326)")
	private Geometry geom;

	@Column(name = "state", length = 50)
	private String state;

	@Column(name = "value_number", precision = 10, scale = 2)
	private BigDecimal valueNumber;

	@Column(name = "source_file", nullable = false, length = 255)
	private String sourceFile;

	public static SourceFeature create(
		String featureType,
		Geometry geom,
		String state,
		BigDecimal valueNumber,
		String sourceFile) {
		SourceFeature sourceFeature = new SourceFeature();
		sourceFeature.featureType = featureType;
		sourceFeature.geom = geom;
		sourceFeature.state = state;
		sourceFeature.valueNumber = valueNumber;
		sourceFeature.sourceFile = sourceFile;
		return sourceFeature;
	}
}
