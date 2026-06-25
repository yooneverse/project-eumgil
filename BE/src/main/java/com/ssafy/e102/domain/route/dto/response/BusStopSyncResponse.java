package com.ssafy.e102.domain.route.dto.response;

public record BusStopSyncResponse(
	int fetchedCount,
	int savedCount,
	int deactivatedCount,
	int activeCount) {
}
