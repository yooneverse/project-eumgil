package com.ssafy.e102.domain.route.entity;

import com.ssafy.e102.domain.route.type.AccessibilityState;
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
@Table(name = "routing_segment_overrides")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoutingSegmentOverride {

	@Id
	@Column(name = "edge_id", nullable = false, updatable = false)
	private Long edgeId;

	@Enumerated(EnumType.STRING)
	@Column(name = "walk_access", length = 30)
	private AccessibilityState walkAccess;

	@Enumerated(EnumType.STRING)
	@Column(name = "stairs_state", length = 30)
	private AccessibilityState stairsState;

	@Enumerated(EnumType.STRING)
	@Column(name = "width_state", length = 30)
	private WidthState widthState;

	@Enumerated(EnumType.STRING)
	@Column(name = "braille_block_state", length = 30)
	private AccessibilityState brailleBlockState;

	@Version
	@Column(name = "version", nullable = false)
	private Long version;

	public static RoutingSegmentOverride of(
		Long edgeId,
		AccessibilityState walkAccess,
		AccessibilityState stairsState,
		WidthState widthState,
		AccessibilityState brailleBlockState) {
		RoutingSegmentOverride override = new RoutingSegmentOverride();
		override.edgeId = edgeId;
		override.walkAccess = walkAccess;
		override.stairsState = stairsState;
		override.widthState = widthState;
		override.brailleBlockState = brailleBlockState;
		return override;
	}

	public void update(
		AccessibilityState walkAccess,
		AccessibilityState stairsState,
		WidthState widthState,
		AccessibilityState brailleBlockState) {
		this.walkAccess = walkAccess;
		this.stairsState = stairsState;
		this.widthState = widthState;
		this.brailleBlockState = brailleBlockState;
	}

	public boolean hasAnyOverride() {
		return walkAccess != null || stairsState != null || widthState != null || brailleBlockState != null;
	}
}
