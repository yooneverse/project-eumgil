package com.ssafy.e102.domain.report.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateHazardReportImageUploadUrlRequest(
	@NotBlank(message = "파일명은 필수입니다.")
	String fileName,
	@NotBlank(message = "이미지 콘텐츠 타입은 필수입니다.")
	String contentType,
	@NotNull(message = "이미지 파일 크기는 필수입니다.") @Positive(message = "이미지 파일 크기는 0보다 커야 합니다.")
	Long contentLength) {
}
