package com.ssafy.e102.domain.place.dto.request;

import com.ssafy.e102.domain.place.type.PlaceClickType;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record PlaceClickDetailRequest(
	@NotNull @DecimalMin("-90.0") @DecimalMax("90.0")
	Double lat,

	@NotNull @DecimalMin("-180.0") @DecimalMax("180.0")
	Double lng,

	@NotNull
	PlaceClickType clickType,

	String provider,
	String providerPlaceId,
	String nameHint) {
}
