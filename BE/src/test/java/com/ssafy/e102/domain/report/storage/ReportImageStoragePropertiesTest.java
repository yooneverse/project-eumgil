package com.ssafy.e102.domain.report.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

class ReportImageStoragePropertiesTest {

	@Test
	@DisplayName("endpoint가 있으면 endpoint를 정규화하고 path-style을 기본 활성화한다")
	void defaultToPathStyleWhenEndpointExists() {
		ReportImageStorageProperties properties = new ReportImageStorageProperties(
			"e102-dev",
			"ap-northeast-2",
			"http://minio:9000/",
			null,
			null,
			"hazard-reports",
			Duration.ofMinutes(10),
			DataSize.ofMegabytes(10),
			List.of("image/jpeg"),
			null);

		assertThat(properties.endpoint()).isEqualTo("http://minio:9000");
		assertThat(properties.pathStyleAccessEnabled()).isTrue();
	}

	@Test
	@DisplayName("access key와 secret key가 있으면 static AWS credential provider를 사용한다")
	void useStaticCredentialsWhenAccessAndSecretKeyExist() {
		ReportImageStorageProperties properties = new ReportImageStorageProperties(
			"e102-report-images",
			"ap-northeast-2",
			null,
			"access-key",
			"secret-key",
			"hazard-reports",
			Duration.ofMinutes(10),
			DataSize.ofMegabytes(10),
			List.of("image/jpeg"),
			false);

		AwsCredentialsProvider provider = ReflectionTestUtils.invokeMethod(
			new ReportImageStorageConfig(),
			"credentialsProvider",
			properties);
		AwsCredentials credentials = provider.resolveCredentials();

		assertThat(credentials.accessKeyId()).isEqualTo("access-key");
		assertThat(credentials.secretAccessKey()).isEqualTo("secret-key");
	}

	@Test
	@DisplayName("key prefix가 슬래시만 남으면 기본 제보 이미지 prefix를 사용한다")
	void defaultKeyPrefixWhenNormalizedPrefixIsBlank() {
		ReportImageStorageProperties properties = new ReportImageStorageProperties(
			"e102-report-images",
			"ap-northeast-2",
			null,
			null,
			null,
			"/",
			Duration.ofMinutes(10),
			DataSize.ofMegabytes(10),
			List.of("image/jpeg"),
			false);

		assertThat(properties.keyPrefix()).isEqualTo("hazard-reports");
	}
}
