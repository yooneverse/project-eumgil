package com.ssafy.e102.domain.report.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ssafy.e102.domain.report.entity.HazardReportImage;

public interface HazardReportImageRepository extends JpaRepository<HazardReportImage, Long> {

	List<HazardReportImage> findAllByHazardReport_ReportIdInAndDisplayOrder(
		Collection<Long> reportIds,
		short displayOrder);

	void deleteAllByHazardReport_User_UserId(UUID userId);
}
