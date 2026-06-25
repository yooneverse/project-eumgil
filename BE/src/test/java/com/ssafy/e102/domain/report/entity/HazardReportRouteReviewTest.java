package com.ssafy.e102.domain.report.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ssafy.e102.domain.report.exception.HazardReportErrorCode;
import com.ssafy.e102.domain.report.exception.HazardReportException;
import com.ssafy.e102.domain.report.type.HazardRouteReviewIntent;
import com.ssafy.e102.domain.route.type.AccessibilityState;
import com.ssafy.e102.domain.route.type.SurfaceState;
import com.ssafy.e102.domain.route.type.WidthState;

class HazardReportRouteReviewTest {

	@Test
	@DisplayName("같은 edgeId draft를 다시 저장하면 기존 draft 엔티티를 갱신한다")
	void replaceSegmentDraftsUpdatesExistingDraft() {
		HazardReportRouteReview review = routeReview();
		review.replaceSegmentDrafts(List.of(draft(
			41231L,
			AccessibilityState.NO,
			WidthState.NARROW,
			SurfaceState.UNPAVED,
			AccessibilityState.YES)));
		HazardReportRouteReviewSegmentDraft existingDraft = review.getSegmentDrafts().get(0);

		review.replaceSegmentDrafts(List.of(draft(
			41231L,
			AccessibilityState.YES,
			WidthState.ADEQUATE_150,
			SurfaceState.PAVED,
			AccessibilityState.NO)));

		assertThat(review.getSegmentDrafts()).hasSize(1);
		assertThat(review.getSegmentDrafts().get(0)).isSameAs(existingDraft);
		assertThat(existingDraft.getWalkAccess()).isEqualTo(AccessibilityState.YES);
		assertThat(existingDraft.getWidthState()).isEqualTo(WidthState.ADEQUATE_150);
		assertThat(existingDraft.getSurfaceState()).isEqualTo(SurfaceState.PAVED);
		assertThat(existingDraft.getStairsState()).isEqualTo(AccessibilityState.NO);
	}

	@Test
	@DisplayName("같은 edgeId draft가 한 요청에 중복되면 검수 요청 오류로 처리한다")
	void replaceSegmentDraftsRejectsDuplicateEdgeIds() {
		HazardReportRouteReview review = routeReview();

		assertThatThrownBy(() -> review.replaceSegmentDrafts(List.of(
			draft(41231L, AccessibilityState.NO, WidthState.NARROW, null, null),
			draft(41231L, AccessibilityState.YES, WidthState.ADEQUATE_150, null, null))))
			.isInstanceOf(HazardReportException.class)
			.extracting("errorCode")
			.isEqualTo(HazardReportErrorCode.INVALID_HAZARD_ROUTE_REVIEW_REQUEST);
	}

	private HazardReportRouteReview routeReview() {
		return HazardReportRouteReview.start(
			mock(HazardReport.class),
			HazardRouteReviewIntent.RESTORE,
			UUID.randomUUID(),
			"부산진구",
			"부전동",
			LocalDateTime.of(2026, 5, 20, 17, 30));
	}

	private HazardReportRouteReviewSegmentDraft draft(
		Long edgeId,
		AccessibilityState walkAccess,
		WidthState widthState,
		SurfaceState surfaceState,
		AccessibilityState stairsState) {
		return HazardReportRouteReviewSegmentDraft.create(
			edgeId,
			walkAccess,
			AccessibilityState.UNKNOWN,
			AccessibilityState.UNKNOWN,
			widthState,
			surfaceState,
			stairsState,
			AccessibilityState.UNKNOWN);
	}
}
