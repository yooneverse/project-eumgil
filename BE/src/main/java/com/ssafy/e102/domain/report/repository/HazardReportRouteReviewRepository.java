package com.ssafy.e102.domain.report.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.e102.domain.admin.dto.response.AdminRoutingApplyStatus;
import com.ssafy.e102.domain.report.entity.HazardReportRouteReview;
import com.ssafy.e102.domain.report.type.HazardRouteReviewStage;

public interface HazardReportRouteReviewRepository extends JpaRepository<HazardReportRouteReview, Long> {

	Optional<HazardReportRouteReview> findTopByHazardReport_ReportIdOrderByReviewIdDesc(Long reportId);

	Optional<HazardReportRouteReview> findTopByHazardReport_ReportIdAndStageOrderByReviewIdDesc(
		Long reportId,
		HazardRouteReviewStage stage);

	@Query("""
		select review
		from HazardReportRouteReview review
		where review.hazardReport.reportId in :reportIds
			and review.reviewId in (
				select max(latest.reviewId)
				from HazardReportRouteReview latest
				where latest.hazardReport.reportId in :reportIds
				group by latest.hazardReport.reportId
			)
		""")
	List<HazardReportRouteReview> findLatestByReportIds(
		@Param("reportIds")
		Collection<Long> reportIds);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
		update HazardReportRouteReview review
		set review.routingApplyStatus = :nextStatus,
			review.routingApplyMessage = :message,
			review.routingAppliedAt = :appliedAt
		where review.routingApplyStatus in :currentStatuses
			and review.completedAt is not null
			and review.completedAt <= :appliedThrough
		""")
	int updateRoutingApplyStatusForCompletedBefore(
		@Param("currentStatuses")
		Collection<AdminRoutingApplyStatus> currentStatuses,
		@Param("nextStatus")
		AdminRoutingApplyStatus nextStatus,
		@Param("message")
		String message,
		@Param("appliedAt")
		LocalDateTime appliedAt,
		@Param("appliedThrough")
		LocalDateTime appliedThrough);
}
