package com.ssafy.e102.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import com.ssafy.e102.domain.report.dto.request.CreateHazardReportImageUploadUrlRequest;
import com.ssafy.e102.domain.report.dto.response.CreateHazardReportImageUploadUrlResponse;
import com.ssafy.e102.domain.report.exception.HazardReportErrorCode;
import com.ssafy.e102.domain.report.exception.HazardReportException;
import com.ssafy.e102.domain.report.storage.ReportImagePresignedUrl;
import com.ssafy.e102.domain.report.storage.ReportImagePresigner;
import com.ssafy.e102.domain.report.storage.ReportImageStorageProperties;

class HazardReportImageUploadServiceTest {

	private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final Instant NOW = Instant.parse("2026-05-14T01:00:00Z");

	@Test
	@DisplayName("제보 이미지 업로드 URL은 사용자 prefix와 파일 타입에 맞는 object key로 발급한다")
	void createUploadUrl() {
		RecordingReportImagePresigner presigner = new RecordingReportImagePresigner();
		HazardReportImageUploadService service = new HazardReportImageUploadService(
			properties(),
			presigner,
			Clock.fixed(NOW, ZoneOffset.UTC));

		CreateHazardReportImageUploadUrlResponse response = service.createUploadUrl(
			USER_ID,
			new CreateHazardReportImageUploadUrlRequest("photo.original.jpg", "image/jpeg", 1024L));

		assertThat(response.uploadUrl()).isEqualTo("https://storage.example.com/upload");
		assertThat(response.objectKey()).isEqualTo(presigner.objectKey);
		assertThat(response.objectKey())
			.startsWith("hazard-reports/11111111-1111-1111-1111-111111111111/20260514/")
			.endsWith(".jpg");
		assertThat(response.expiresAt()).isEqualTo(Instant.parse("2026-05-14T01:10:00Z"));
		assertThat(presigner.contentType).isEqualTo("image/jpeg");
		assertThat(presigner.contentLength).isEqualTo(1024L);
		assertThat(presigner.signatureDuration).isEqualTo(Duration.ofMinutes(10));
	}

	@Test
	void createUploadUrls() {
		RecordingReportImagePresigner presigner = new RecordingReportImagePresigner();
		HazardReportImageUploadService service = new HazardReportImageUploadService(
			properties(),
			presigner,
			Clock.fixed(NOW, ZoneOffset.UTC));

		List<CreateHazardReportImageUploadUrlResponse> responses = service.createUploadUrls(
			USER_ID,
			List.of(
				new CreateHazardReportImageUploadUrlRequest("photo-1.jpg", "image/jpeg", 1024L),
				new CreateHazardReportImageUploadUrlRequest("photo-2.webp", "image/webp", 2048L)));

		assertThat(responses).hasSize(2);
		assertThat(responses.get(0).uploadUrl()).isEqualTo("https://storage.example.com/upload");
		assertThat(responses.get(0).objectKey()).startsWith("hazard-reports/11111111-1111-1111-1111-111111111111/20260514/");
		assertThat(responses.get(1).objectKey()).endsWith(".webp");
		assertThat(presigner.putRequests).hasSize(2);
	}

	@Test
	@DisplayName("저장된 object key는 조회 시점에 presigned GET URL로 변환한다")
	void createReadUrl() {
		RecordingReportImagePresigner presigner = new RecordingReportImagePresigner();
		HazardReportImageUploadService service = new HazardReportImageUploadService(
			properties(),
			presigner,
			Clock.fixed(NOW, ZoneOffset.UTC));

		String readUrl = service.createReadUrl(
			"hazard-reports/11111111-1111-1111-1111-111111111111/20260514/image.jpg");

		assertThat(readUrl).isEqualTo("https://storage.example.com/read");
		assertThat(presigner.readObjectKey)
			.isEqualTo("hazard-reports/11111111-1111-1111-1111-111111111111/20260514/image.jpg");
		assertThat(presigner.readSignatureDuration).isEqualTo(Duration.ofMinutes(10));
	}

	@Test
	@DisplayName("기존 DB에 공개 URL이 저장되어 있어도 path를 object key로 정규화해 조회 URL을 발급한다")
	void createReadUrlFromLegacyPublicUrl() {
		RecordingReportImagePresigner presigner = new RecordingReportImagePresigner();
		HazardReportImageUploadService service = new HazardReportImageUploadService(
			properties(),
			presigner,
			Clock.fixed(NOW, ZoneOffset.UTC));

		service.createReadUrl(
			"https://e102-report-images.s3.ap-northeast-2.amazonaws.com/hazard-reports/"
				+ "11111111-1111-1111-1111-111111111111/20260514/image.jpg");

		assertThat(presigner.readObjectKey)
			.isEqualTo("hazard-reports/11111111-1111-1111-1111-111111111111/20260514/image.jpg");
	}

	@Test
	@DisplayName("기존 path-style URL은 bucket segment를 제거하고 object key로 정규화한다")
	void createReadUrlFromLegacyPathStyleUrl() {
		RecordingReportImagePresigner presigner = new RecordingReportImagePresigner();
		HazardReportImageUploadService service = new HazardReportImageUploadService(
			properties(),
			presigner,
			Clock.fixed(NOW, ZoneOffset.UTC));

		service.createReadUrl(
			"http://localhost:9000/e102-report-images/hazard-reports/"
				+ "11111111-1111-1111-1111-111111111111/20260514/image.jpg");

		assertThat(presigner.readObjectKey)
			.isEqualTo("hazard-reports/11111111-1111-1111-1111-111111111111/20260514/image.jpg");
	}

	@Test
	@DisplayName("조회 URL도 제보 이미지 prefix 밖의 object key에는 발급하지 않는다")
	void rejectReadUrlOutsideReportImagePrefix() {
		HazardReportImageUploadService service = service();

		assertThatThrownBy(() -> service.createReadUrl(
			"https://e102-report-images.s3.ap-northeast-2.amazonaws.com/private/image.jpg"))
			.isInstanceOf(HazardReportException.class)
			.extracting("errorCode")
			.isEqualTo(HazardReportErrorCode.INVALID_HAZARD_REPORT_IMAGE_URL);
	}

	@Test
	@DisplayName("허용하지 않는 이미지 타입은 업로드 URL을 발급하지 않는다")
	void rejectUnsupportedContentType() {
		HazardReportImageUploadService service = service();

		assertThatThrownBy(() -> service.createUploadUrl(
			USER_ID,
			new CreateHazardReportImageUploadUrlRequest("photo.gif", "image/gif", 1024L)))
			.isInstanceOf(HazardReportException.class)
			.extracting("errorCode")
			.isEqualTo(HazardReportErrorCode.INVALID_HAZARD_REPORT_IMAGE_UPLOAD_REQUEST);
	}

	@Test
	@DisplayName("최대 용량을 넘는 이미지는 업로드 URL을 발급하지 않는다")
	void rejectTooLargeContentLength() {
		HazardReportImageUploadService service = service();

		assertThatThrownBy(() -> service.createUploadUrl(
			USER_ID,
			new CreateHazardReportImageUploadUrlRequest("photo.jpg", "image/jpeg", DataSize.ofMegabytes(11).toBytes())))
			.isInstanceOf(HazardReportException.class)
			.extracting("errorCode")
			.isEqualTo(HazardReportErrorCode.INVALID_HAZARD_REPORT_IMAGE_UPLOAD_REQUEST);
	}

	@Test
	@DisplayName("내 사용자 prefix의 이미지 object key는 제보 첨부로 허용한다")
	void validateImageObjectKeys() {
		HazardReportImageUploadService service = service();

		service.validateImageObjectKeys(
			USER_ID,
			List.of("hazard-reports/11111111-1111-1111-1111-111111111111/20260514/image.jpg"));
	}

	@Test
	@DisplayName("외부 URL이나 다른 사용자 object key는 제보 첨부로 허용하지 않는다")
	void rejectExternalImageObjectKey() {
		HazardReportImageUploadService service = service();

		assertThatThrownBy(() -> service.validateImageObjectKeys(
			USER_ID,
			List.of("https://attacker.example.com/image.jpg")))
			.isInstanceOf(HazardReportException.class)
			.extracting("errorCode")
			.isEqualTo(HazardReportErrorCode.INVALID_HAZARD_REPORT_IMAGE_URL);

		assertThatThrownBy(() -> service.validateImageObjectKeys(
			USER_ID,
			List.of("hazard-reports/22222222-2222-2222-2222-222222222222/20260514/image.jpg")))
			.isInstanceOf(HazardReportException.class)
			.extracting("errorCode")
			.isEqualTo(HazardReportErrorCode.INVALID_HAZARD_REPORT_IMAGE_URL);
	}

	@Test
	@DisplayName("query나 fragment가 붙은 object key는 제보 첨부로 허용하지 않는다")
	void rejectSignedUrlLikeObjectKey() {
		HazardReportImageUploadService service = service();

		assertThatThrownBy(() -> service.validateImageObjectKeys(
			USER_ID,
			List.of("hazard-reports/11111111-1111-1111-1111-111111111111/20260514/image.jpg?sig=1")))
			.isInstanceOf(HazardReportException.class)
			.extracting("errorCode")
			.isEqualTo(HazardReportErrorCode.INVALID_HAZARD_REPORT_IMAGE_URL);
	}

	@Test
	@DisplayName("제보 이미지는 최대 5장까지만 첨부할 수 있다")
	void rejectMoreThanFiveImageObjectKeys() {
		HazardReportImageUploadService service = service();

		assertThatThrownBy(() -> service.validateImageObjectKeys(
			USER_ID,
			List.of(
				"hazard-reports/11111111-1111-1111-1111-111111111111/20260514/image-1.jpg",
				"hazard-reports/11111111-1111-1111-1111-111111111111/20260514/image-2.jpg",
				"hazard-reports/11111111-1111-1111-1111-111111111111/20260514/image-3.jpg",
				"hazard-reports/11111111-1111-1111-1111-111111111111/20260514/image-4.jpg",
				"hazard-reports/11111111-1111-1111-1111-111111111111/20260514/image-5.jpg",
				"hazard-reports/11111111-1111-1111-1111-111111111111/20260514/image-6.jpg")))
			.isInstanceOf(HazardReportException.class)
			.extracting("errorCode")
			.isEqualTo(HazardReportErrorCode.INVALID_HAZARD_REPORT_IMAGE_URL);
	}

	private HazardReportImageUploadService service() {
		return new HazardReportImageUploadService(
			properties(),
			new RecordingReportImagePresigner(),
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private ReportImageStorageProperties properties() {
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

	private static class RecordingReportImagePresigner implements ReportImagePresigner {

		private String objectKey;
		private String contentType;
		private long contentLength;
		private Duration signatureDuration;
		private String readObjectKey;
		private Duration readSignatureDuration;
		private final List<String> putRequests = new ArrayList<>();

		@Override
		public ReportImagePresignedUrl createPutObjectPresignedUrl(
			String objectKey,
			String contentType,
			long contentLength,
			Duration signatureDuration) {
			this.putRequests.add(objectKey);
			this.objectKey = objectKey;
			this.contentType = contentType;
			this.contentLength = contentLength;
			this.signatureDuration = signatureDuration;
			return new ReportImagePresignedUrl(
				"https://storage.example.com/upload",
				Instant.parse("2026-05-14T01:10:00Z"));
		}

		@Override
		public ReportImagePresignedUrl createGetObjectPresignedUrl(
			String objectKey,
			Duration signatureDuration) {
			this.readObjectKey = objectKey;
			this.readSignatureDuration = signatureDuration;
			return new ReportImagePresignedUrl(
				"https://storage.example.com/read",
				Instant.parse("2026-05-14T01:10:00Z"));
		}
	}
}
