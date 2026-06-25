package com.ssafy.e102.global.external.odsay;

import java.util.List;

public record OdsayTransitSearchResult(
	List<OdsayTransitPath> paths) {
}
