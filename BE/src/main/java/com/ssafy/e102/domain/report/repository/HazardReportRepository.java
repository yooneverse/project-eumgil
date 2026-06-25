package com.ssafy.e102.domain.report.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.e102.domain.report.entity.HazardReport;
import com.ssafy.e102.domain.report.type.ReportStatus;
import com.ssafy.e102.domain.report.type.ReportType;

public interface HazardReportRepository extends JpaRepository<HazardReport, Long> {

	Slice<HazardReport> findAllByUser_UserId(UUID userId, Pageable pageable);

	Slice<HazardReport> findAllByUser_UserIdAndReportIdLessThan(UUID userId, Long reportId, Pageable pageable);

	@EntityGraph(attributePaths = "images")
	Optional<HazardReport> findWithImagesByReportId(Long reportId);

	@Query(value = """
		select *
		from hazard_reports
		where status = 'APPROVED'
			and ST_Intersects(
				report_point,
				ST_MakeEnvelope(:swLng, :swLat, :neLng, :neLat, 4326)
			)
		order by report_id desc
		""", nativeQuery = true)
	List<HazardReport> findApprovedWithinBounds(
		@Param("swLng")
		double swLng,
		@Param("swLat")
		double swLat,
		@Param("neLng")
		double neLng,
		@Param("neLat")
		double neLat,
		Pageable pageable);

	@EntityGraph(attributePaths = "images")
	List<HazardReport> findAllByReportIdIn(List<Long> reportIds);

	Optional<HazardReport> findByUser_UserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

	@EntityGraph(attributePaths = "user")
	@Query("select hazardReport from HazardReport hazardReport")
	Slice<HazardReport> findAllForAdmin(Pageable pageable);

	@EntityGraph(attributePaths = "user")
	@Query("select hazardReport from HazardReport hazardReport order by hazardReport.createdAt desc, hazardReport.reportId desc")
	List<HazardReport> findRecentForDashboard(Pageable pageable);

	@EntityGraph(attributePaths = "user")
	@Query("select hazardReport from HazardReport hazardReport where hazardReport.reportId < :reportId")
	Slice<HazardReport> findAllByReportIdLessThanForAdmin(
		@Param("reportId")
		Long reportId,
		Pageable pageable);

	@EntityGraph(attributePaths = "user")
	@Query("select hazardReport from HazardReport hazardReport where hazardReport.status = :status")
	Slice<HazardReport> findAllByStatusForAdmin(
		@Param("status")
		ReportStatus status,
		Pageable pageable);

	@EntityGraph(attributePaths = "user")
	@Query("""
		select hazardReport
		from HazardReport hazardReport
		where hazardReport.status = :status
			and hazardReport.reportId < :reportId
		""")
	Slice<HazardReport> findAllByStatusAndReportIdLessThanForAdmin(
		@Param("status")
		ReportStatus status,
		@Param("reportId")
		Long reportId,
		Pageable pageable);

	@EntityGraph(attributePaths = {"images", "user"})
	Optional<HazardReport> findWithImagesAndUserByReportId(Long reportId);

	long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime from, LocalDateTime to);

	long countByStatus(ReportStatus status);

	@Query("""
		select hazardReport.reportType as reportType,
			count(hazardReport) as count
		from HazardReport hazardReport
		group by hazardReport.reportType
		""")
	List<ReportTypeCount> countByReportType();

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update HazardReport hazardReport
			set hazardReport.status = :nextStatus,
				hazardReport.processedByUserId = :processedByUserId,
				hazardReport.processedAt = :processedAt,
				hazardReport.updatedAt = current_timestamp
			where hazardReport.reportId = :reportId
				and hazardReport.status = :currentStatus
		""")
	int updateStatusIfCurrentStatus(
		@Param("reportId")
		Long reportId,
		@Param("currentStatus")
		ReportStatus currentStatus,
		@Param("nextStatus")
		ReportStatus nextStatus,
		@Param("processedByUserId")
		UUID processedByUserId,
		@Param("processedAt")
		LocalDateTime processedAt);

	@Modifying(flushAutomatically = true)
	@Query("""
			update HazardReport hazardReport
			set hazardReport.idempotencyKey = null,
				hazardReport.idempotencyRequestHash = null,
				hazardReport.idempotencyExpiresAt = null
			where hazardReport.idempotencyExpiresAt <= :now
		""")
	int clearExpiredIdempotencyMetadata(
		@Param("now")
		LocalDateTime now);

	void deleteAllByUser_UserId(UUID userId);

	interface ReportTypeCount {
		ReportType getReportType();

		long getCount();
	}
}
