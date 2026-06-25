package com.ssafy.e102.domain.report.entity;

import com.ssafy.e102.domain.route.type.AccessibilityState;
import com.ssafy.e102.domain.route.type.SurfaceState;
import com.ssafy.e102.domain.route.type.WidthState;
import com.ssafy.e102.global.exception.BusinessException;
import com.ssafy.e102.global.exception.CommonErrorCode;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "hazard_report_route_review_segment_drafts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HazardReportRouteReviewSegmentDraft {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "draft_id", nullable = false, updatable = false)
	private Long draftId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "review_id", nullable = false)
	private HazardReportRouteReview review;

	@Column(name = "edge_id", nullable = false)
	private Long edgeId;

	@Enumerated(EnumType.STRING)
	@Column(name = "walk_access", length = 30)
	private AccessibilityState walkAccess;

	@Enumerated(EnumType.STRING)
	@Column(name = "braille_block_state", length = 30)
	private AccessibilityState brailleBlockState;

	@Enumerated(EnumType.STRING)
	@Column(name = "audio_signal_state", length = 30)
	private AccessibilityState audioSignalState;

	@Enumerated(EnumType.STRING)
	@Column(name = "width_state", length = 30)
	private WidthState widthState;

	@Enumerated(EnumType.STRING)
	@Column(name = "surface_state", length = 30)
	private SurfaceState surfaceState;

	@Enumerated(EnumType.STRING)
	@Column(name = "stairs_state", length = 30)
	private AccessibilityState stairsState;

	@Enumerated(EnumType.STRING)
	@Column(name = "signal_state", length = 30)
	private AccessibilityState signalState;

	public static HazardReportRouteReviewSegmentDraft create(
		Long edgeId,
		AccessibilityState walkAccess,
		AccessibilityState brailleBlockState,
		AccessibilityState audioSignalState,
		WidthState widthState,
		SurfaceState surfaceState,
		AccessibilityState stairsState,
		AccessibilityState signalState) {
		HazardReportRouteReviewSegmentDraft draft = new HazardReportRouteReviewSegmentDraft();
		draft.edgeId = requireEdgeId(edgeId);
		draft.walkAccess = walkAccess;
		draft.brailleBlockState = brailleBlockState;
		draft.audioSignalState = audioSignalState;
		draft.widthState = widthState;
		draft.surfaceState = surfaceState;
		draft.stairsState = stairsState;
		draft.signalState = signalState;
		return draft;
	}

	void updateAttributesFrom(HazardReportRouteReviewSegmentDraft draft) {
		this.walkAccess = draft.walkAccess;
		this.brailleBlockState = draft.brailleBlockState;
		this.audioSignalState = draft.audioSignalState;
		this.widthState = draft.widthState;
		this.surfaceState = draft.surfaceState;
		this.stairsState = draft.stairsState;
		this.signalState = draft.signalState;
	}

	void attach(HazardReportRouteReview review) {
		this.review = review;
	}

	private static Long requireEdgeId(Long edgeId) {
		if (edgeId == null || edgeId <= 0) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, "검수 대상 edgeId가 올바르지 않습니다.");
		}
		return edgeId;
	}
}
