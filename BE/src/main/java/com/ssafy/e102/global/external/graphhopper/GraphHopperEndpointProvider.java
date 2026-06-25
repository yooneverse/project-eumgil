package com.ssafy.e102.global.external.graphhopper;

/**
 * GraphHopper runtime endpoint 선택 경계다.
 *
 * <p>운영에서는 Redis active slot을 읽고, 테스트나 Redis 장애 상황에서는 고정 fallback endpoint를 쓴다.
 */
public interface GraphHopperEndpointProvider {

	GraphHopperEndpointSelection selectEndpoint();
}
