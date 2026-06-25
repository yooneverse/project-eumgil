package com.ssafy.e102.domain.report.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.locationtech.jts.geom.Point;

import com.ssafy.e102.domain.report.exception.HazardReportErrorCode;
import com.ssafy.e102.domain.report.exception.HazardReportException;
import com.ssafy.e102.domain.report.type.ReportStatus;
import com.ssafy.e102.domain.report.type.ReportType;
import com.ssafy.e102.domain.user.entity.User;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "hazard_reports", uniqueConstraints = {
	@UniqueConstraint(name = "uk_hazard_reports_user_id_idempotency_key", columnNames = {"user_id", "idempotency_key"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HazardReport extends BaseEntity {

	private static final int MAX_IMAGE_COUNT = 5;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "report_id", nullable = false, updatable = false)
	private Long reportId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(name = "report_type", nullable = false, length = 30)
	private ReportType reportType;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(length = 255)
	private String address;

	@Column(name = "idempotency_key", length = 255)
	private String idempotencyKey;

	@Column(name = "idempotency_request_hash", length = 64)
	private String idempotencyRequestHash;

	@Column(name = "idempotency_expires_at")
	private LocalDateTime idempotencyExpiresAt;

	@Column(name = "report_point", nullable = false, columnDefinition = "geometry(Point, 4326)")
	private Point reportPoint;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ReportStatus status;

	@Column(name = "processed_by_user_id")
	private UUID processedByUserId;

	@Column(name = "processed_at")
	private LocalDateTime processedAt;

	@OrderBy("displayOrder ASC")
	@OneToMany(mappedBy = "hazardReport", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<HazardReportImage> images = new ArrayList<>();

	public static HazardReport create(
		User user,
		ReportType reportType,
		String description,
		Point reportPoint,
		List<String> imageObjectKeys) {
		return create(user, reportType, description, null, reportPoint, imageObjectKeys, List.of());
	}

	public static HazardReport create(
		User user,
		ReportType reportType,
		String description,
		String address,
		Point reportPoint,
		List<String> imageObjectKeys) {
		return create(user, reportType, description, address, reportPoint, imageObjectKeys, List.of());
	}

	public static HazardReport create(
		User user,
		ReportType reportType,
		String description,
		String address,
		Point reportPoint,
		List<String> imageObjectKeys,
		List<String> thumbnailObjectKeys) {
		HazardReport hazardReport = new HazardReport();
		hazardReport.user = requireUser(user);
		hazardReport.reportType = requireReportType(reportType);
		hazardReport.description = normalizeDescription(description);
		hazardReport.address = normalizeAddress(address);
		hazardReport.reportPoint = requirePoint(reportPoint);
		hazardReport.status = ReportStatus.PENDING;
		hazardReport.addImages(valueOrEmpty(imageObjectKeys), valueOrEmpty(thumbnailObjectKeys));
		return hazardReport;
	}

	public boolean isOwner(UUID userId) {
		return user != null && Objects.equals(user.getUserId(), userId);
	}

	public void applyIdempotency(String idempotencyKey, String requestHash, LocalDateTime expiresAt) {
		if (idempotencyKey == null) {
			return;
		}
		if (idempotencyKey.isBlank() || requestHash == null || requestHash.isBlank() || expiresAt == null) {
			throw invalidRequest("멱등성 정보가 올바르지 않습니다.");
		}
		this.idempotencyKey = idempotencyKey;
		this.idempotencyRequestHash = requestHash;
		this.idempotencyExpiresAt = expiresAt;
	}

	public boolean hasActiveIdempotency(LocalDateTime now) {
		return idempotencyKey != null
			&& idempotencyExpiresAt != null
			&& idempotencyExpiresAt.isAfter(now);
	}

	public boolean hasSameIdempotencyRequestHash(String requestHash) {
		return Objects.equals(idempotencyRequestHash, requestHash);
	}

	public void approve() {
		approve(null, null);
	}

	public void approve(UUID processedByUserId, LocalDateTime processedAt) {
		validateApprovableStatus();
		status = ReportStatus.APPROVED;
		recordProcessing(processedByUserId, processedAt);
	}

	public void reject() {
		reject(null, null);
	}

	public void reject(UUID processedByUserId, LocalDateTime processedAt) {
		validatePendingStatus();
		status = ReportStatus.REJECTED;
		recordProcessing(processedByUserId, processedAt);
	}

	public void markProcessed(UUID processedByUserId, LocalDateTime processedAt) {
		recordProcessing(processedByUserId, processedAt);
	}

	private void validatePendingStatus() {
		if (status != ReportStatus.PENDING) {
			throw new HazardReportException(HazardReportErrorCode.HAZARD_REPORT_ALREADY_PROCESSED);
		}
	}

	private void validateApprovableStatus() {
		if (status != ReportStatus.PENDING && status != ReportStatus.REJECTED) {
			throw new HazardReportException(HazardReportErrorCode.HAZARD_REPORT_ALREADY_PROCESSED);
		}
	}

	private void recordProcessing(UUID processedByUserId, LocalDateTime processedAt) {
		this.processedByUserId = processedByUserId;
		this.processedAt = processedAt;
	}

	private void addImages(List<String> imageObjectKeys, List<String> thumbnailObjectKeys) {
		if (imageObjectKeys.size() > MAX_IMAGE_COUNT) {
			throw invalidRequest("제보 이미지는 최대 5장까지 등록할 수 있습니다.");
		}
		if (!thumbnailObjectKeys.isEmpty() && thumbnailObjectKeys.size() != imageObjectKeys.size()) {
			throw invalidRequest("제보 썸네일 object key 개수는 원본 이미지 개수와 같아야 합니다.");
		}
		for (int index = 0; index < imageObjectKeys.size(); index++) {
			String thumbnailObjectKey = thumbnailObjectKeys.isEmpty() ? null : thumbnailObjectKeys.get(index);
			images.add(HazardReportImage.create(this, imageObjectKeys.get(index), thumbnailObjectKey, index));
		}
	}

	private static User requireUser(User user) {
		if (user == null) {
			throw invalidRequest("사용자는 필수입니다.");
		}
		return user;
	}

	private static ReportType requireReportType(ReportType reportType) {
		if (reportType == null) {
			throw invalidRequest("제보 유형은 필수입니다.");
		}
		return reportType;
	}

	private static Point requirePoint(Point reportPoint) {
		if (reportPoint == null) {
			throw invalidRequest("제보 위치는 필수입니다.");
		}
		return reportPoint;
	}

	private static String normalizeDescription(String description) {
		if (description == null || description.isBlank()) {
			return null;
		}
		return description.trim();
	}

	private static String normalizeAddress(String address) {
		if (address == null || address.isBlank()) {
			return null;
		}
		return address.trim();
	}

	private static List<String> valueOrEmpty(List<String> imageObjectKeys) {
		if (imageObjectKeys == null) {
			return List.of();
		}
		return imageObjectKeys;
	}

	private static HazardReportException invalidRequest(String message) {
		return new HazardReportException(HazardReportErrorCode.INVALID_HAZARD_REPORT_REQUEST, message);
	}
}
