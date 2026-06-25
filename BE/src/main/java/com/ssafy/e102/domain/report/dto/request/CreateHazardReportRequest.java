package com.ssafy.e102.domain.report.dto.request;

import java.util.List;

import com.ssafy.e102.domain.report.type.ReportType;
import com.ssafy.e102.global.geo.dto.GeoPointRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateHazardReportRequest(
	@NotNull(message = "제보 유형은 필수입니다.")
	ReportType reportType,

	String description,

	@Valid @NotNull(message = "제보 위치는 필수입니다.")
	GeoPointRequest reportPoint,

	@Size(max = 5, message = "제보 이미지는 최대 5장까지 등록할 수 있습니다.")
	List<@NotBlank(message = "제보 이미지 object key는 비어 있을 수 없습니다.") String> imageObjectKeys,

	@Size(max = 5, message = "제보 썸네일 object key는 최대 5장까지 등록할 수 있습니다.")
	List<@NotBlank(message = "제보 썸네일 object key는 비어 있을 수 없습니다.") String> thumbnailObjectKeys) {
}
