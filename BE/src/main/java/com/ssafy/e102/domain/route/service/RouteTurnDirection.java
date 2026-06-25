package com.ssafy.e102.domain.route.service;

/**
 * route geometry에서 계산한 step 진행 방향 안내다.
 *
 * <p>이 값은 API alert가 아니라 step instruction 문구를 만들 때만 사용한다.
 */
public enum RouteTurnDirection {
	LEFT("좌회전하세요."),
	RIGHT("우회전하세요.");

	private final String instruction;

	RouteTurnDirection(String instruction) {
		this.instruction = instruction;
	}

	public String instruction() {
		return instruction;
	}
}
