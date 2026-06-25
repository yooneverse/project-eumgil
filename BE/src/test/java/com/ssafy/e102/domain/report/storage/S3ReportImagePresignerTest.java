package com.ssafy.e102.domain.report.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3ReportImagePresignerTest {

	@Test
	@DisplayName("S3 presigner는 private bucket용 업로드와 조회 URL을 모두 서명한다")
	void createPutAndGetPresignedUrls() {
		ReportImageStorageProperties properties = properties();
		try (S3Presigner s3Presigner = S3Presigner.builder()
			.region(Region.of(properties.region()))
			.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
				properties.accessKey(),
				properties.secretKey())))
			.serviceConfiguration(S3Configuration.builder()
				.pathStyleAccessEnabled(properties.pathStyleAccessEnabled())
				.build())
			.build()) {
			S3ReportImagePresigner presigner = new S3ReportImagePresigner(s3Presigner, properties);
			String objectKey = "hazard-reports/user-1/20260515/image.png";

			ReportImagePresignedUrl uploadUrl = presigner.createPutObjectPresignedUrl(
				objectKey,
				"image/png",
				1024L,
				Duration.ofMinutes(10));
			ReportImagePresignedUrl readUrl = presigner.createGetObjectPresignedUrl(
				objectKey,
				Duration.ofMinutes(10));

			assertThat(uploadUrl.url())
				.startsWith("https://busan-eumgil-s3.s3.ap-northeast-2.amazonaws.com/" + objectKey)
				.contains("X-Amz-Signature=");
			assertThat(readUrl.url())
				.startsWith("https://busan-eumgil-s3.s3.ap-northeast-2.amazonaws.com/" + objectKey)
				.contains("X-Amz-Signature=");
			assertThat(uploadUrl.expiresAt()).isNotNull();
			assertThat(readUrl.expiresAt()).isNotNull();
		}
	}

	private ReportImageStorageProperties properties() {
		return new ReportImageStorageProperties(
			"busan-eumgil-s3",
			"ap-northeast-2",
			null,
			"test-access-key",
			"test-secret-key",
			"hazard-reports",
			Duration.ofMinutes(10),
			DataSize.ofMegabytes(10),
			List.of("image/jpeg", "image/png"),
			false);
	}
}
