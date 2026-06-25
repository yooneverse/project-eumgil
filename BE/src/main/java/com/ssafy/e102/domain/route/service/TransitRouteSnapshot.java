package com.ssafy.e102.domain.route.service;

import java.util.List;
import java.util.Map;

public record TransitRouteSnapshot(
	String routeId,
	String mapObj,
	List<Map<String, Object>> legs) {
}
