package com.ssafy.e102.domain.admin.type;

import com.ssafy.e102.domain.route.type.WalkRouteProfile;

public enum AdminRouteProfileGroup {
	PEDESTRIAN(WalkRouteProfile.PEDESTRIAN_SAFE, WalkRouteProfile.PEDESTRIAN_FAST),
	VISUAL(WalkRouteProfile.VISUAL_SAFE, WalkRouteProfile.VISUAL_FAST),
	WHEELCHAIR_MANUAL(WalkRouteProfile.WHEELCHAIR_MANUAL_SAFE, WalkRouteProfile.WHEELCHAIR_MANUAL_FAST),
	WHEELCHAIR_AUTO(WalkRouteProfile.WHEELCHAIR_AUTO_SAFE, WalkRouteProfile.WHEELCHAIR_AUTO_FAST);

	private final WalkRouteProfile safeProfile;
	private final WalkRouteProfile fastProfile;

	AdminRouteProfileGroup(WalkRouteProfile safeProfile, WalkRouteProfile fastProfile) {
		this.safeProfile = safeProfile;
		this.fastProfile = fastProfile;
	}

	public WalkRouteProfile getSafeProfile() {
		return safeProfile;
	}

	public WalkRouteProfile getFastProfile() {
		return fastProfile;
	}
}
