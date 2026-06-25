package com.ssafy.e102.domain.route.service;

import com.ssafy.e102.domain.user.type.MobilitySubtype;
import com.ssafy.e102.domain.user.type.PrimaryUserType;

public record WalkRouteUserProfile(
	PrimaryUserType primaryUserType,
	MobilitySubtype mobilitySubtype) {
}
