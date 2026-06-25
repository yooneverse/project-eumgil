package com.ssafy.e102.domain.report.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateHazardReportImageUploadBatchRequest(
	@NotNull(message = "업로드 파일 목록은 필수입니다.")
	@NotEmpty(message = "업로드 파일은 최소 한 개 이상이어야 합니다.")
	@Size(max = 5, message = "업로드 파일은 최대 5개까지 가능합니다.")
	List<@Valid CreateHazardReportImageUploadUrlRequest> files) {
}
