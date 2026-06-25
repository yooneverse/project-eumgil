package com.ssafy.e102.global.geo;

import com.ssafy.e102.global.geo.dto.GeoPointRequest;

/**
 * WGS84 위경도 좌표 사이의 지표면 거리를 계산한다.
 *
 * <p>경로 검색의 출발/도착 근접 검증과 GraphHopper geometry 기반 step 거리 배분이 같은 하버사인 공식을 쓰도록 한다.
 */
public final class GeoDistanceCalculator {

	private static final double EARTH_RADIUS_METER = 6_371_000.0;

	private GeoDistanceCalculator() {}

	public static double distanceMeter(GeoPointRequest from, GeoPointRequest to) {
		return distanceMeter(from.lat(), from.lng(), to.lat(), to.lng());
	}

	public static double distanceMeter(double fromLat, double fromLng, double toLat, double toLng) {
		double startLat = Math.toRadians(fromLat);
		double endLat = Math.toRadians(toLat);
		double deltaLat = Math.toRadians(toLat - fromLat);
		double deltaLng = Math.toRadians(toLng - fromLng);
		double haversine = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
			+ Math.cos(startLat) * Math.cos(endLat) * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
		double angularDistance = 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
		return EARTH_RADIUS_METER * angularDistance;
	}
}
