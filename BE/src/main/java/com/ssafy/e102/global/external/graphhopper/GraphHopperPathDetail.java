package com.ssafy.e102.global.external.graphhopper;

/**
 * GraphHopper path details의 index range 하나를 route 도메인에서 쓰기 쉬운 값으로 정리한 모델이다.
 */
public record GraphHopperPathDetail(
	int fromIndex,
	int toIndex,
	String value) {
}
