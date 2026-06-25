package com.ssafy.e102.domain.route.entity;

import java.math.BigDecimal;

import org.locationtech.jts.geom.Geometry;

import com.ssafy.e102.domain.route.type.SegmentFeatureType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 횡단보도, 음향신호기, 점자블록, 계단처럼 실제 위치 안내가 필요한 원천 feature다.
 *
 * <p>경사, 노면, 폭은 위치 이벤트가 아니라 {@code road_segments} 집계 컬럼으로만 관리하므로 이 Entity의
 * {@code featureType} 후보에 포함하지 않는다.
 */
@Getter
@Entity
@Table(name = "segment_features")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SegmentFeature {

	@Id
	@Column(name = "feature_id", nullable = false, updatable = false)
	private Long featureId;

	@Column(name = "edge_id", nullable = false)
	private Long edgeId;

	@Enumerated(EnumType.STRING)
	@Column(name = "feature_type", nullable = false, length = 50)
	private SegmentFeatureType featureType;

	@Column(name = "geom", nullable = false, columnDefinition = "geometry(Geometry, 4326)")
	private Geometry geom;

	@Column(name = "state", length = 50)
	private String state;

	@Column(name = "value_number", precision = 10, scale = 2)
	private BigDecimal valueNumber;

	public static SegmentFeature create(
		Long featureId,
		Long edgeId,
		SegmentFeatureType featureType,
		Geometry geom,
		String state,
		BigDecimal valueNumber) {
		SegmentFeature segmentFeature = new SegmentFeature();
		segmentFeature.featureId = featureId;
		segmentFeature.edgeId = edgeId;
		segmentFeature.featureType = featureType;
		segmentFeature.geom = geom;
		segmentFeature.state = state;
		segmentFeature.valueNumber = valueNumber;
		return segmentFeature;
	}
}
