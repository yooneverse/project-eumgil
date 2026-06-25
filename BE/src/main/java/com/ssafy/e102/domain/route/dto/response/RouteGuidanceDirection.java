package com.ssafy.e102.domain.route.dto.response;

/**
 * route geometry에서 계산한 진행 방향 보조 정보다.
 *
 * <p>접근성/시설 이벤트가 같은 지점에 있으면 {@link RouteGuidanceEventResponse#type()}이 주 안내가 되고,
 * 이 값은 방향 아이콘이나 보조 배지로만 사용한다.
 */
public enum RouteGuidanceDirection {
	STRAIGHT,
	TURN_LEFT,
	TURN_RIGHT
}
