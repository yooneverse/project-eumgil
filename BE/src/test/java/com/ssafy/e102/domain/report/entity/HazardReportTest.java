package com.ssafy.e102.domain.report.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ssafy.e102.domain.report.exception.HazardReportErrorCode;
import com.ssafy.e102.domain.report.exception.HazardReportException;
import com.ssafy.e102.domain.report.type.ReportStatus;
import com.ssafy.e102.domain.report.type.ReportType;
import com.ssafy.e102.domain.user.entity.User;
import com.ssafy.e102.domain.user.type.PrimaryUserType;
import com.ssafy.e102.domain.user.type.SocialProvider;
import com.ssafy.e102.global.geo.GeoPointConverter;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

class HazardReportTest {

	private final GeoPointConverter geoPointConverter = new GeoPointConverter();

	@Test
	@DisplayName("도로 상태 제보는 기본 PENDING 상태와 이미지 순서를 가진다")
	void createHazardReport() {
		User user = user(UUID.randomUUID());

		HazardReport hazardReport = HazardReport.create(
			user,
			ReportType.SIDEWALK_MISSING,
			" 보행 가능한 인도가 없습니다. ",
			geoPointConverter.toPoint(new GeoPointRequest(35.1686, 129.0576)),
			List.of(
				" hazard-reports/user-1/20260514/image-1.jpg ",
				"hazard-reports/user-1/20260514/image-2.jpg"));

		assertThat(hazardReport.getUser()).isEqualTo(user);
		assertThat(hazardReport.getReportType()).isEqualTo(ReportType.SIDEWALK_MISSING);
		assertThat(hazardReport.getDescription()).isEqualTo("보행 가능한 인도가 없습니다.");
		assertThat(hazardReport.getStatus()).isEqualTo(ReportStatus.PENDING);
		assertThat(hazardReport.getImages()).hasSize(2);
		assertThat(hazardReport.getImages().get(0).getImageObjectKey())
			.isEqualTo("hazard-reports/user-1/20260514/image-1.jpg");
		assertThat(hazardReport.getImages().get(0).getDisplayOrder()).isZero();
		assertThat(hazardReport.getImages().get(1).getDisplayOrder()).isEqualTo((short)1);
	}

	@Test
	@DisplayName("설명이 비어 있으면 null로 저장한다")
	void blankDescriptionBecomesNull() {
		HazardReport hazardReport = HazardReport.create(
			user(UUID.randomUUID()),
			ReportType.RAMP,
			" ",
			geoPointConverter.toPoint(new GeoPointRequest(35.1686, 129.0576)),
			null);

		assertThat(hazardReport.getDescription()).isNull();
		assertThat(hazardReport.getImages()).isEmpty();
	}

	@Test
	@DisplayName("제보 이미지는 최대 5장까지 허용한다")
	void rejectTooManyImages() {
		assertThatThrownBy(() -> HazardReport.create(
			user(UUID.randomUUID()),
			ReportType.RAMP,
			null,
			geoPointConverter.toPoint(new GeoPointRequest(35.1686, 129.0576)),
			List.of("1", "2", "3", "4", "5", "6")))
			.isInstanceOf(HazardReportException.class)
			.extracting("errorCode")
			.isEqualTo(HazardReportErrorCode.INVALID_HAZARD_REPORT_REQUEST);
	}

	@Test
	@DisplayName("도로 상태 제보 소유자를 판별한다")
	void isOwner() {
		UUID userId = UUID.randomUUID();
		HazardReport hazardReport = HazardReport.create(
			user(userId),
			ReportType.SIDEWALK_WIDTH,
			null,
			geoPointConverter.toPoint(new GeoPointRequest(35.1686, 129.0576)),
			null);

		assertThat(hazardReport.isOwner(userId)).isTrue();
		assertThat(hazardReport.isOwner(UUID.randomUUID())).isFalse();
	}

	@Test
	@DisplayName("PENDING 제보는 승인할 수 있다")
	void approve() {
		HazardReport hazardReport = HazardReport.create(
			user(UUID.randomUUID()),
			ReportType.SIDEWALK_MISSING,
			null,
			geoPointConverter.toPoint(new GeoPointRequest(35.1686, 129.0576)),
			null);

		hazardReport.approve();

		assertThat(hazardReport.getStatus()).isEqualTo(ReportStatus.APPROVED);
	}

	@Test
	@DisplayName("PENDING 제보는 반려할 수 있다")
	void reject() {
		HazardReport hazardReport = HazardReport.create(
			user(UUID.randomUUID()),
			ReportType.SIDEWALK_MISSING,
			null,
			geoPointConverter.toPoint(new GeoPointRequest(35.1686, 129.0576)),
			null);

		hazardReport.reject();

		assertThat(hazardReport.getStatus()).isEqualTo(ReportStatus.REJECTED);
	}

	@Test
	@DisplayName("이미 처리된 제보는 다시 처리할 수 없다")
	void rejectAlreadyProcessedReport() {
		HazardReport hazardReport = HazardReport.create(
			user(UUID.randomUUID()),
			ReportType.SIDEWALK_MISSING,
			null,
			geoPointConverter.toPoint(new GeoPointRequest(35.1686, 129.0576)),
			null);
		hazardReport.approve();

		assertThatThrownBy(hazardReport::reject)
			.isInstanceOf(HazardReportException.class)
			.extracting("errorCode")
			.isEqualTo(HazardReportErrorCode.HAZARD_REPORT_ALREADY_PROCESSED);
	}

	@Test
	@DisplayName("반려된 제보는 다시 승인 검수를 거쳐 APPROVED로 변경할 수 있다")
	void approveRejectedReport() {
		UUID reviewerUserId = UUID.randomUUID();
		LocalDateTime processedAt = LocalDateTime.of(2026, 5, 18, 14, 0);
		HazardReport hazardReport = HazardReport.create(
			user(UUID.randomUUID()),
			ReportType.SIDEWALK_MISSING,
			null,
			geoPointConverter.toPoint(new GeoPointRequest(35.1686, 129.0576)),
			null);
		hazardReport.reject();

		hazardReport.approve(reviewerUserId, processedAt);

		assertThat(hazardReport.getStatus()).isEqualTo(ReportStatus.APPROVED);
		assertThat(hazardReport.getProcessedByUserId()).isEqualTo(reviewerUserId);
		assertThat(hazardReport.getProcessedAt()).isEqualTo(processedAt);
	}

	private User user(UUID userId) {
		User user = User.create(SocialProvider.KAKAO, "kakao-user-id", PrimaryUserType.LOW_VISION, null);
		ReflectionTestUtils.setField(user, "userId", userId);
		return user;
	}
}
