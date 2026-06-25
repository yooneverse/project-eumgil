package com.ssafy.e102.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.unit.DataSize;

import com.ssafy.e102.domain.report.dto.request.CreateHazardReportImageUploadUrlRequest;
import com.ssafy.e102.domain.report.dto.request.CreateHazardReportRequest;
import com.ssafy.e102.domain.report.dto.response.CreateHazardReportImageUploadUrlResponse;
import com.ssafy.e102.domain.report.dto.response.HazardReportDetailResponse;
import com.ssafy.e102.domain.report.dto.response.HazardReportIdResponse;
import com.ssafy.e102.domain.report.dto.response.HazardReportListResponse;
import com.ssafy.e102.domain.report.entity.HazardReport;
import com.ssafy.e102.domain.report.repository.HazardReportImageRepository;
import com.ssafy.e102.domain.report.repository.HazardReportRepository;
import com.ssafy.e102.domain.report.storage.ReportImagePresignedUrl;
import com.ssafy.e102.domain.report.storage.ReportImagePresigner;
import com.ssafy.e102.domain.report.storage.ReportImageStorageProperties;
import com.ssafy.e102.domain.report.type.ReportStatus;
import com.ssafy.e102.domain.report.type.ReportType;
import com.ssafy.e102.domain.user.entity.User;
import com.ssafy.e102.domain.user.repository.UserRepository;
import com.ssafy.e102.domain.user.type.PrimaryUserType;
import com.ssafy.e102.domain.user.type.SocialProvider;
import com.ssafy.e102.global.external.kakao.KakaoAddressDocument;
import com.ssafy.e102.global.external.kakao.KakaoLocalClient;
import com.ssafy.e102.global.geo.GeoPointConverter;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

class HazardReportFeFlowTest {

	private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final Clock FIXED_CLOCK = Clock.fixed(
		Instant.parse("2026-05-14T01:00:00Z"),
		ZoneOffset.UTC);
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 14, 1, 0);

	@Test
	@DisplayName("FE 제보 플로우는 업로드 URL, objectKey 제출, 멱등 재시도, 조회용 presigned imageUrls를 연결한다")
	void reportFlowFromImageUploadToSubmitAndReadBack() {
		HazardReportRepository hazardReportRepository = mock(HazardReportRepository.class);
		HazardReportImageRepository hazardReportImageRepository = mock(HazardReportImageRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		KakaoLocalClient kakaoLocalClient = mock(KakaoLocalClient.class);
		HazardReportImageUploadService imageUploadService = new HazardReportImageUploadService(
			storageProperties(),
			new FixedReportImagePresigner(),
			FIXED_CLOCK);
		HazardReportService hazardReportService = new HazardReportService(
			hazardReportRepository,
			hazardReportImageRepository,
			userRepository,
			new GeoPointConverter(),
			imageUploadService,
			kakaoLocalClient,
			FIXED_CLOCK,
			testTransactionOperations());
		User user = user();
		AtomicLong reportIds = new AtomicLong(100L);
		AtomicReference<HazardReport> savedReport = new AtomicReference<>();

		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
		when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
		when(kakaoLocalClient.reverseGeocode(35.1686, 129.0576))
			.thenReturn(Optional.of(new KakaoAddressDocument(
				"부산 부산진구 범전동 200",
				"부산 부산진구 시민공원로 73",
				null,
				"부산",
				"부산진구",
				"범전동")));
		when(hazardReportRepository.findByUser_UserIdAndIdempotencyKey(USER_ID, "outbox-report-1"))
			.thenAnswer(invocation -> Optional.ofNullable(savedReport.get()));
		when(hazardReportRepository.save(any(HazardReport.class))).thenAnswer(invocation -> {
			HazardReport hazardReport = invocation.getArgument(0);
			ReflectionTestUtils.setField(hazardReport, "reportId", reportIds.getAndIncrement());
			ReflectionTestUtils.setField(hazardReport, "createdAt", NOW);
			savedReport.set(hazardReport);
			return hazardReport;
		});
		when(hazardReportRepository.findAllByUser_UserId(eq(USER_ID), any(Pageable.class)))
			.thenAnswer(invocation -> new SliceImpl<>(
				List.of(savedReport.get()),
				invocation.getArgument(1),
				false));
		when(hazardReportImageRepository.findAllByHazardReport_ReportIdInAndDisplayOrder(
			eq(List.of(100L)),
			eq((short)0)))
			.thenAnswer(invocation -> List.of(savedReport.get().getImages().get(0)));
		when(hazardReportRepository.findWithImagesByReportId(100L))
			.thenAnswer(invocation -> Optional.ofNullable(savedReport.get()));

		CreateHazardReportImageUploadUrlResponse upload = imageUploadService.createUploadUrl(
			USER_ID,
			new CreateHazardReportImageUploadUrlRequest("report-photo.jpg", "image/jpeg", 1024L));
		String readUrl = "https://storage.example.com/read?key=" + upload.objectKey();
		String longDescription = "가".repeat(81);
		CreateHazardReportRequest submitRequest = new CreateHazardReportRequest(
			ReportType.SIDEWALK_MISSING,
			longDescription,
			new GeoPointRequest(35.1686, 129.0576),
			List.of(upload.objectKey()),
			List.of());

		HazardReportIdResponse created = hazardReportService.createHazardReport(
			USER_ID,
			submitRequest,
			" outbox-report-1 ");
		HazardReportIdResponse retried = hazardReportService.createHazardReport(
			USER_ID,
			submitRequest,
			"outbox-report-1");
		HazardReportListResponse list = hazardReportService.getMyHazardReports(USER_ID, null, 10);
		HazardReportDetailResponse detail = hazardReportService.getMyHazardReportDetail(USER_ID, 100L);

		assertThat(upload.uploadUrl()).isEqualTo("https://storage.example.com/upload");
		assertThat(upload.objectKey()).startsWith(
			"hazard-reports/11111111-1111-1111-1111-111111111111/20260514/");
		assertThat(created.reportId()).isEqualTo(100L);
		assertThat(retried.reportId()).isEqualTo(100L);
		assertThat(list.content()).hasSize(1);
		assertThat(list.content().get(0).status()).isEqualTo(ReportStatus.PENDING);
		assertThat(list.content().get(0).address()).isEqualTo("부산 부산진구 시민공원로 73");
		assertThat(list.content().get(0).description()).isEqualTo("가".repeat(80) + "...");
		assertThat(list.content().get(0).representativeImageUrl()).isEqualTo(readUrl);
		assertThat(detail.description()).isEqualTo(longDescription);
		assertThat(detail.status()).isEqualTo(ReportStatus.PENDING);
		assertThat(detail.imageUrls()).containsExactly(readUrl);
		assertThat(savedReport.get().getImages().get(0).getImageObjectKey()).isEqualTo(upload.objectKey());
		assertThat(savedReport.get().getIdempotencyKey()).isEqualTo("outbox-report-1");
		assertThat(savedReport.get().getIdempotencyExpiresAt()).isEqualTo(NOW.plusHours(24));
		verify(hazardReportRepository).clearExpiredIdempotencyMetadata(NOW);
		verify(hazardReportRepository).save(any(HazardReport.class));
	}

	private ReportImageStorageProperties storageProperties() {
		return new ReportImageStorageProperties(
			"e102-report-images",
			"ap-northeast-2",
			null,
			null,
			null,
			"hazard-reports",
			Duration.ofMinutes(10),
			DataSize.ofMegabytes(10),
			List.of("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif"),
			false);
	}

	private User user() {
		User user = User.create(SocialProvider.KAKAO, "kakao-user-id", PrimaryUserType.LOW_VISION, null);
		ReflectionTestUtils.setField(user, "userId", USER_ID);
		return user;
	}

	private TransactionOperations testTransactionOperations() {
		return new TransactionOperations() {

			@Override
			public <T> T execute(TransactionCallback<T> action) {
				return action.doInTransaction(new SimpleTransactionStatus());
			}
		};
	}

	private static class FixedReportImagePresigner implements ReportImagePresigner {

		@Override
		public ReportImagePresignedUrl createPutObjectPresignedUrl(
			String objectKey,
			String contentType,
			long contentLength,
			Duration signatureDuration) {
			return new ReportImagePresignedUrl(
				"https://storage.example.com/upload",
				Instant.parse("2026-05-14T01:10:00Z"));
		}

		@Override
		public ReportImagePresignedUrl createGetObjectPresignedUrl(
			String objectKey,
			Duration signatureDuration) {
			return new ReportImagePresignedUrl(
				"https://storage.example.com/read?key=" + objectKey,
				Instant.parse("2026-05-14T01:10:00Z"));
		}
	}
}
