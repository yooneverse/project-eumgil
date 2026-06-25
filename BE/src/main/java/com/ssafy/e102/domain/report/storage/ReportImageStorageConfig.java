package com.ssafy.e102.domain.report.storage;

import java.net.URI;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(ReportImageStorageProperties.class)
public class ReportImageStorageConfig {

	@Bean
	public S3Presigner reportImageS3Presigner(ReportImageStorageProperties properties) {
		S3Presigner.Builder builder = S3Presigner.builder()
			.region(Region.of(properties.region()))
			.credentialsProvider(credentialsProvider(properties))
			.serviceConfiguration(S3Configuration.builder()
				.pathStyleAccessEnabled(properties.pathStyleAccessEnabled())
				.build());

		if (StringUtils.hasText(properties.endpoint())) {
			builder.endpointOverride(URI.create(properties.endpoint()));
		}

		return builder.build();
	}

	private AwsCredentialsProvider credentialsProvider(ReportImageStorageProperties properties) {
		if (properties.hasStaticCredentials()) {
			return StaticCredentialsProvider.create(AwsBasicCredentials.create(
				properties.accessKey(),
				properties.secretKey()));
		}
		return DefaultCredentialsProvider.builder().build();
	}
}
