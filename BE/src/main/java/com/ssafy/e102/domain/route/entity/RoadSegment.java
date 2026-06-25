package com.ssafy.e102.domain.route.entity;

import java.math.BigDecimal;

import org.locationtech.jts.geom.LineString;

import com.ssafy.e102.domain.route.type.AccessibilityState;
import com.ssafy.e102.domain.route.type.SegmentType;
import com.ssafy.e102.domain.route.type.SurfaceState;
import com.ssafy.e102.domain.route.type.WidthState;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "road_segments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoadSegment {

	@Id
	@Column(name = "edge_id", nullable = false, updatable = false)
	private Long edgeId;

	@Column(name = "from_node_id", nullable = false)
	private Long fromNodeId;

	@Column(name = "to_node_id", nullable = false)
	private Long toNodeId;

	@Column(name = "geom", nullable = false, columnDefinition = "geometry(LineString, 4326)")
	private LineString geom;

	@Column(name = "length_meter", nullable = false, precision = 10, scale = 2)
	private BigDecimal lengthMeter;

	@Enumerated(EnumType.STRING)
	@Column(name = "walk_access", nullable = false, length = 30, columnDefinition = "varchar(30) default 'UNKNOWN'")
	private AccessibilityState walkAccess = AccessibilityState.UNKNOWN;

	@Column(name = "avg_slope_percent", precision = 6, scale = 2)
	private BigDecimal avgSlopePercent;

	@Column(name = "width_meter", precision = 6, scale = 2)
	private BigDecimal widthMeter;

	@Enumerated(EnumType.STRING)
	@Column(name = "braille_block_state", nullable = false, length = 30, columnDefinition = "varchar(30) default 'UNKNOWN'")
	private AccessibilityState brailleBlockState = AccessibilityState.UNKNOWN;

	@Enumerated(EnumType.STRING)
	@Column(name = "audio_signal_state", nullable = false, length = 30, columnDefinition = "varchar(30) default 'UNKNOWN'")
	private AccessibilityState audioSignalState = AccessibilityState.UNKNOWN;

	@Enumerated(EnumType.STRING)
	@Column(name = "width_state", nullable = false, length = 30, columnDefinition = "varchar(30) default 'UNKNOWN'")
	private WidthState widthState = WidthState.UNKNOWN;

	@Enumerated(EnumType.STRING)
	@Column(name = "surface_state", nullable = false, length = 30, columnDefinition = "varchar(30) default 'UNKNOWN'")
	private SurfaceState surfaceState = SurfaceState.UNKNOWN;

	@Enumerated(EnumType.STRING)
	@Column(name = "stairs_state", nullable = false, length = 30, columnDefinition = "varchar(30) default 'UNKNOWN'")
	private AccessibilityState stairsState = AccessibilityState.UNKNOWN;

	@Enumerated(EnumType.STRING)
	@Column(name = "signal_state", nullable = false, length = 30, columnDefinition = "varchar(30) default 'UNKNOWN'")
	private AccessibilityState signalState = AccessibilityState.UNKNOWN;

	@Enumerated(EnumType.STRING)
	@Column(name = "segment_type", nullable = false, length = 30, columnDefinition = "varchar(30) default 'SIDE_LINE'")
	private SegmentType segmentType = SegmentType.SIDE_LINE;

	@Version
	@Column(name = "version", nullable = false)
	private Long version;

	public static RoadSegment create(
		Long edgeId,
		Long fromNodeId,
		Long toNodeId,
		LineString geom,
		BigDecimal lengthMeter) {
		RoadSegment roadSegment = new RoadSegment();
		roadSegment.edgeId = edgeId;
		roadSegment.fromNodeId = fromNodeId;
		roadSegment.toNodeId = toNodeId;
		roadSegment.geom = geom;
		roadSegment.lengthMeter = lengthMeter;
		return roadSegment;
	}

	public void updateAttributes(
		AccessibilityState walkAccess,
		AccessibilityState brailleBlockState,
		AccessibilityState audioSignalState,
		WidthState widthState,
		SurfaceState surfaceState,
		AccessibilityState stairsState,
		AccessibilityState signalState) {
		if (walkAccess != null) {
			this.walkAccess = walkAccess;
		}
		if (brailleBlockState != null) {
			this.brailleBlockState = brailleBlockState;
		}
		if (audioSignalState != null) {
			this.audioSignalState = audioSignalState;
		}
		if (widthState != null) {
			this.widthState = widthState;
		}
		if (surfaceState != null) {
			this.surfaceState = surfaceState;
		}
		if (stairsState != null) {
			this.stairsState = stairsState;
		}
		if (signalState != null) {
			this.signalState = signalState;
		}
	}
}
