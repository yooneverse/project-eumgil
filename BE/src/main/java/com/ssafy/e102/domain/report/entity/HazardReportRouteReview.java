package com.ssafy.e102.domain.report.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ssafy.e102.domain.admin.dto.response.AdminRoutingApplyStatus;
import com.ssafy.e102.domain.report.exception.HazardReportErrorCode;
import com.ssafy.e102.domain.report.exception.HazardReportException;
import com.ssafy.e102.domain.report.type.HazardRouteReviewIntent;
import com.ssafy.e102.domain.report.type.HazardRouteReviewStage;
import com.ssafy.e102.global.entity.BaseEntity;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "hazard_report_route_reviews")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HazardReportRouteReview extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "review_id", nullable = false, updatable = false)
	private Long reviewId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "report_id", nullable = false)
	private HazardReport hazardReport;

	@Enumerated(EnumType.STRING)
	@Column(name = "intent", nullable = false, length = 30)
	private HazardRouteReviewIntent intent;

	@Enumerated(EnumType.STRING)
	@Column(name = "stage", nullable = false, length = 30)
	private HazardRouteReviewStage stage;

	@Column(name = "reviewer_user_id", nullable = false)
	private UUID reviewerUserId;

	@Column(name = "gu", nullable = false, length = 40)
	private String gu;

	@Column(name = "dong", nullable = false, length = 80)
	private String dong;

	@Column(name = "started_at", nullable = false)
	private LocalDateTime startedAt;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "routing_apply_status", length = 30)
	private AdminRoutingApplyStatus routingApplyStatus;

	@Column(name = "routing_apply_message", columnDefinition = "TEXT")
	private String routingApplyMessage;

	@Column(name = "routing_applied_at")
	private LocalDateTime routingAppliedAt;

	@Column(name = "selected_segment_edge_id")
	private Long selectedSegmentEdgeId;

	@OrderBy("edgeId ASC")
	@OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<HazardReportRouteReviewSegmentDraft> segmentDrafts = new ArrayList<>();

	public static HazardReportRouteReview start(
		HazardReport hazardReport,
		HazardRouteReviewIntent intent,
		UUID reviewerUserId,
		String gu,
		String dong,
		LocalDateTime now) {
		HazardReportRouteReview review = new HazardReportRouteReview();
		review.hazardReport = requireHazardReport(hazardReport);
		review.intent = requireIntent(intent);
		review.reviewerUserId = requireReviewerUserId(reviewerUserId);
		review.gu = requireText(gu, "검수 구는 필수입니다.");
		review.dong = requireText(dong, "검수 동은 필수입니다.");
		review.stage = HazardRouteReviewStage.IN_PROGRESS;
		review.startedAt = requireTimestamp(now);
		return review;
	}

	public boolean isInProgress() {
		return stage == HazardRouteReviewStage.IN_PROGRESS;
	}

	public boolean isCompleted() {
		return stage == HazardRouteReviewStage.COMPLETED;
	}

	public void selectSegment(Long edgeId) {
		if (edgeId == null) {
			return;
		}
		selectedSegmentEdgeId = edgeId;
	}

	public void replaceSegmentDrafts(List<HazardReportRouteReviewSegmentDraft> drafts) {
		if (drafts == null || drafts.isEmpty()) {
			segmentDrafts.clear();
			return;
		}
		Map<Long, HazardReportRouteReviewSegmentDraft> draftsByEdgeId = toDraftsByEdgeId(drafts);
		Map<Long, HazardReportRouteReviewSegmentDraft> existingDraftsByEdgeId = toDraftsByEdgeId(segmentDrafts);

		segmentDrafts.removeIf(draft -> !draftsByEdgeId.containsKey(draft.getEdgeId()));
		for (HazardReportRouteReviewSegmentDraft draft : draftsByEdgeId.values()) {
			HazardReportRouteReviewSegmentDraft existingDraft = existingDraftsByEdgeId.get(draft.getEdgeId());
			if (existingDraft != null) {
				existingDraft.updateAttributesFrom(draft);
				continue;
			}
			addSegmentDraft(draft);
		}
	}

	public void complete(LocalDateTime now) {
		validateInProgress();
		if (segmentDrafts.isEmpty()) {
			throw new HazardReportException(
				HazardReportErrorCode.INVALID_HAZARD_ROUTE_REVIEW_REQUEST,
				"최소 1개 세그먼트를 검수해야 처리 완료할 수 있습니다.");
		}
		stage = HazardRouteReviewStage.COMPLETED;
		completedAt = requireTimestamp(now);
	}

	public void recordRoutingApplyStatus(
		AdminRoutingApplyStatus status,
		String message,
		LocalDateTime appliedAt) {
		routingApplyStatus = status;
		routingApplyMessage = message;
		routingAppliedAt = appliedAt;
	}

	public void continueBy(UUID userId) {
		validateInProgress();
		reviewerUserId = requireReviewerUserId(userId);
	}

	private void validateInProgress() {
		if (stage != HazardRouteReviewStage.IN_PROGRESS) {
			throw new HazardReportException(
				HazardReportErrorCode.HAZARD_ROUTE_REVIEW_CONFLICT,
				"진행 중인 경로 검수만 수정할 수 있습니다.");
		}
	}

	private void addSegmentDraft(HazardReportRouteReviewSegmentDraft draft) {
		HazardReportRouteReviewSegmentDraft requiredDraft = requireDraft(draft);
		requiredDraft.attach(this);
		segmentDrafts.add(requiredDraft);
	}

	private static HazardReport requireHazardReport(HazardReport hazardReport) {
		if (hazardReport == null) {
			throw new HazardReportException(HazardReportErrorCode.INVALID_HAZARD_ROUTE_REVIEW_REQUEST, "제보는 필수입니다.");
		}
		return hazardReport;
	}

	private static HazardRouteReviewIntent requireIntent(HazardRouteReviewIntent intent) {
		if (intent == null) {
			throw new HazardReportException(HazardReportErrorCode.INVALID_HAZARD_ROUTE_REVIEW_REQUEST, "검수 유형은 필수입니다.");
		}
		return intent;
	}

	private static UUID requireReviewerUserId(UUID reviewerUserId) {
		if (reviewerUserId == null) {
			throw new HazardReportException(HazardReportErrorCode.HAZARD_ROUTE_REVIEW_CONFLICT, "검수 관리자 정보가 필요합니다.");
		}
		return reviewerUserId;
	}

	private static String requireText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new HazardReportException(HazardReportErrorCode.INVALID_HAZARD_ROUTE_REVIEW_REQUEST, message);
		}
		return value.trim();
	}

	private static LocalDateTime requireTimestamp(LocalDateTime value) {
		if (value == null) {
			throw new HazardReportException(
				HazardReportErrorCode.INVALID_HAZARD_ROUTE_REVIEW_REQUEST,
				"검수 시각이 올바르지 않습니다.");
		}
		return value;
	}

	private static HazardReportRouteReviewSegmentDraft requireDraft(HazardReportRouteReviewSegmentDraft draft) {
		if (draft == null) {
			throw new HazardReportException(
				HazardReportErrorCode.INVALID_HAZARD_ROUTE_REVIEW_REQUEST,
				"세그먼트 draft가 올바르지 않습니다.");
		}
		return draft;
	}

	private static Map<Long, HazardReportRouteReviewSegmentDraft> toDraftsByEdgeId(
		List<HazardReportRouteReviewSegmentDraft> drafts) {
		Map<Long, HazardReportRouteReviewSegmentDraft> draftsByEdgeId = new LinkedHashMap<>();
		for (HazardReportRouteReviewSegmentDraft draft : drafts) {
			HazardReportRouteReviewSegmentDraft requiredDraft = requireDraft(draft);
			HazardReportRouteReviewSegmentDraft previousDraft = draftsByEdgeId.putIfAbsent(
				requiredDraft.getEdgeId(),
				requiredDraft);
			if (previousDraft != null) {
				throw new HazardReportException(
					HazardReportErrorCode.INVALID_HAZARD_ROUTE_REVIEW_REQUEST,
					"같은 세그먼트 draft를 중복 저장할 수 없습니다.");
			}
		}
		return draftsByEdgeId;
	}
}
