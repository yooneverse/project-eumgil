package com.ssafy.e102.domain.route.type;

/**
 * Backend 사용자 유형을 GraphHopper custom profile 이름으로 연결하는 내부 enum이다.
 *
 * <p>WalkRouteProfileService가 이 값을 결정하고, GraphHopperRouteClient가 profileName을 `/route` query에 싣는다.
 */
public enum WalkRouteProfile {
	PEDESTRIAN_SAFE("pedestrian_safe"),
	PEDESTRIAN_FAST("pedestrian_fast"),
	VISUAL_SAFE("visual_safe"),
	VISUAL_FAST("visual_fast"),
	WHEELCHAIR_MANUAL_SAFE("wheelchair_manual_safe"),
	WHEELCHAIR_MANUAL_FAST("wheelchair_manual_fast"),
	WHEELCHAIR_AUTO_SAFE("wheelchair_auto_safe"),
	WHEELCHAIR_AUTO_FAST("wheelchair_auto_fast");

	private final String profileName;

	WalkRouteProfile(String profileName) {
		this.profileName = profileName;
	}

	public String getProfileName() {
		return profileName;
	}
}
