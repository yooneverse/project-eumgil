package com.ssafy.e102.global.external.bims;

import java.util.List;

public record BusanBimsBusStopPage(
	List<BusanBimsBusStop> busStops,
	int totalCount,
	int pageNo,
	int numOfRows) {
}
