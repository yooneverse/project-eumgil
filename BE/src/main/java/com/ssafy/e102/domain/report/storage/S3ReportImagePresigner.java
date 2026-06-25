package com.ssafy.e102.domain.report.storage;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.ssafy.e102.domain.report.exception.HazardReportErrorCode;
import com.ssafy.e102.domain.report.exception.HazardReportException;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
@RequiredArgsConstructor
public class S3ReportImagePresigner implements ReportImagePresigner {

	private final S3Presigner s3Presigner;
	private final ReportImageStorageProperties properties;

	@Override
	public ReportImagePresignedUrl createPutObjectPresignedUrl(
		String objectKey,
		String contentType,
		long contentLength,
		Duration signatureDuration) {
		try {
			PutObjectRequest putObjectRequest = PutObjectRequest.builder()
				.bucket(properties.bucket())
				.key(objectKey)
				.contentType(contentType)
				.contentLength(contentLength)
				.build();
			PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
				.signatureDuration(signatureDuration)
				.putObjectRequest(putObjectRequest)
				.build();
			PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

			return new ReportImagePresignedUrl(
				presignedRequest.url().toString(),
				presignedRequest.expiration());
		} catch (SdkException | IllegalArgumentException exception) {
			throw new HazardReportException(
				HazardReportErrorCode.HAZARD_REPORT_IMAGE_UPLOAD_UNAVAILABLE,
				"제보 이미지 업로드 URL을 발급할 수 없습니다.",
				exception);
		}
	}

	@Override
	public ReportImagePresignedUrl createGetObjectPresignedUrl(
		String objectKey,
		Duration signatureDuration) {
		try {
			GetObjectRequest getObjectRequest = GetObjectRequest.builder()
				.bucket(properties.bucket())
				.key(objectKey)
				.build();
			GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
				.signatureDuration(signatureDuration)
				.getObjectRequest(getObjectRequest)
				.build();
			PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);

			return new ReportImagePresignedUrl(
				presignedRequest.url().toString(),
				presignedRequest.expiration());
		} catch (SdkException | IllegalArgumentException exception) {
			throw new HazardReportException(
				HazardReportErrorCode.HAZARD_REPORT_IMAGE_UPLOAD_UNAVAILABLE,
				"제보 이미지 조회 URL을 발급할 수 없습니다.",
				exception);
		}
	}
}
