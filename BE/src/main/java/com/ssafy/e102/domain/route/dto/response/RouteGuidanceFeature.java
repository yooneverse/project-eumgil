package com.ssafy.e102.domain.route.dto.response;

/**
 * 주 안내 이벤트에 붙는 부가 시설/속성 정보다.
 *
 * <p>예를 들어 횡단보도 안내의 주 타입은 {@link RouteGuidanceEventType#CROSSWALK}로 유지하고,
 * 신호기와 음향신호기는 features로 표현한다.
 */
public enum RouteGuidanceFeature {
	SIGNAL,
	AUDIO_SIGNAL
}
