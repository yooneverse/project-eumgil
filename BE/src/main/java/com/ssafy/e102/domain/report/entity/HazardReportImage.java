package com.ssafy.e102.domain.report.entity;

import com.ssafy.e102.domain.report.exception.HazardReportErrorCode;
import com.ssafy.e102.domain.report.exception.HazardReportException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "hazard_report_images", uniqueConstraints = {
	@UniqueConstraint(name = "uk_hazard_report_images_report_display_order", columnNames = {"report_id",
		"display_order"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HazardReportImage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "report_img_id", nullable = false, updatable = false)
	private Long reportImgId;

	@Column(name = "image_url", nullable = false, columnDefinition = "TEXT")
	private String imageObjectKey;

	@Column(name = "thumbnail_url", columnDefinition = "TEXT")
	private String thumbnailObjectKey;

	@Column(name = "display_order", nullable = false)
	private short displayOrder;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "report_id", nullable = false)
	private HazardReport hazardReport;

	public static HazardReportImage create(
		HazardReport hazardReport,
		String imageObjectKey,
		String thumbnailObjectKey,
		int displayOrder) {
		HazardReportImage hazardReportImage = new HazardReportImage();
		hazardReportImage.hazardReport = requireHazardReport(hazardReport);
		hazardReportImage.imageObjectKey = normalizeImageObjectKey(imageObjectKey);
		hazardReportImage.thumbnailObjectKey = normalizeThumbnailObjectKey(thumbnailObjectKey);
		hazardReportImage.displayOrder = (short)displayOrder;
		return hazardReportImage;
	}

	private static HazardReport requireHazardReport(HazardReport hazardReport) {
		if (hazardReport == null) {
			throw invalidRequest("제보는 필수입니다.");
		}
		return hazardReport;
	}

	private static String normalizeImageObjectKey(String imageObjectKey) {
		if (imageObjectKey == null || imageObjectKey.isBlank()) {
			throw invalidRequest("제보 이미지 object key는 필수입니다.");
		}
		return imageObjectKey.trim();
	}

	private static String normalizeThumbnailObjectKey(String thumbnailObjectKey) {
		if (thumbnailObjectKey == null || thumbnailObjectKey.isBlank()) {
			return null;
		}
		return thumbnailObjectKey.trim();
	}

	private static HazardReportException invalidRequest(String message) {
		return new HazardReportException(HazardReportErrorCode.INVALID_HAZARD_REPORT_REQUEST, message);
	}
}
