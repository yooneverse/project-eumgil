package com.ssafy.e102.domain.route.dto.response;

/**
 * WALK leg 위에서 FE가 알림 카드와 TTS로 소비할 주 안내 이벤트 유형이다.
 *
 * <p>방향 전환은 {@link RouteGuidanceDirection}으로 분리해 접근성/시설 이벤트와 같은 지점에서
 * 보조 방향 정보로 함께 표현한다.
 */
public enum RouteGuidanceEventType {
	CROSSWALK,
	STRAIGHT,
	LOW_SLOPE,
	MIDDLE_SLOPE,
	STAIR,
	NARROW_SIDEWALK,
	UNPAVED,
	BUS_STOP,
	SUBWAY_ELEVATOR,
	ARRIVING_POINT,
	DESTINATION
}
