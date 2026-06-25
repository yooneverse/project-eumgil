package com.ssafy.e102.domain.route.service;

import org.springframework.stereotype.Service;

import com.ssafy.e102.domain.route.exception.RouteErrorCode;
import com.ssafy.e102.domain.route.exception.RouteException;
import com.ssafy.e102.domain.route.type.RouteOption;
import com.ssafy.e102.domain.route.type.WalkRouteProfile;
import com.ssafy.e102.domain.user.type.MobilitySubtype;
import com.ssafy.e102.domain.user.type.PrimaryUserType;

/**
 * 사용자 테이블에서 조회한 장애/이동 유형과 routeOption을 GraphHopper profile로 바꾼다.
 *
 * <p>RouteController 또는 상위 route service가 사용자 유형을 넘기면, 이 서비스는 도보 경로 검색에 사용할
 * {@link WalkRouteProfile}만 결정해서 GraphHopper 호출 단계로 넘긴다.
 */
@Service
public class WalkRouteProfileService {

	public WalkRouteProfile resolve(
		PrimaryUserType primaryUserType,
		MobilitySubtype mobilitySubtype,
		RouteOption routeOption) {
		if (routeOption == null) {
			throw invalidRequest("경로 옵션은 필수입니다.");
		}
		// 장애 대분류를 먼저 고정한 뒤, 이동 보조 유형이 필요한 사용자만 세부 profile로 분기한다.
		return switch (requirePrimaryUserType(primaryUserType)) {
			case LOW_VISION -> resolveLowVision(mobilitySubtype, routeOption);
			case MOBILITY_IMPAIRED -> resolveMobilityImpaired(mobilitySubtype, routeOption);
		};
	}

	public String resolveProfileName(
		PrimaryUserType primaryUserType,
		MobilitySubtype mobilitySubtype,
		RouteOption routeOption) {
		return resolve(primaryUserType, mobilitySubtype, routeOption).getProfileName();
	}

	private PrimaryUserType requirePrimaryUserType(PrimaryUserType primaryUserType) {
		if (primaryUserType == null) {
			throw invalidRequest("사용자 유형은 필수입니다.");
		}
		return primaryUserType;
	}

	private WalkRouteProfile resolveLowVision(MobilitySubtype mobilitySubtype, RouteOption routeOption) {
		if (mobilitySubtype != null) {
			throw invalidRequest("저시력자는 보행약자 세부 유형을 가질 수 없습니다.");
		}
		return switch (routeOption) {
			case SAFE -> WalkRouteProfile.VISUAL_SAFE;
			case SHORTEST -> WalkRouteProfile.VISUAL_FAST;
			default -> throw invalidRequest("도보 경로 옵션이 아닙니다.");
		};
	}

	private WalkRouteProfile resolveMobilityImpaired(MobilitySubtype mobilitySubtype, RouteOption routeOption) {
		if (mobilitySubtype == null) {
			throw invalidRequest("보행약자는 보행약자 세부 유형이 필요합니다.");
		}
		return switch (mobilitySubtype) {
			case POWER_WHEELCHAIR -> resolvePowerWheelchair(routeOption);
			case MANUAL_WHEELCHAIR -> resolveManualWheelchair(routeOption);
			case OTHER_MOBILITY -> resolveOtherMobility(routeOption);
		};
	}

	private WalkRouteProfile resolvePowerWheelchair(RouteOption routeOption) {
		return switch (routeOption) {
			case SAFE -> WalkRouteProfile.WHEELCHAIR_AUTO_SAFE;
			case SHORTEST -> WalkRouteProfile.WHEELCHAIR_AUTO_FAST;
			default -> throw invalidRequest("도보 경로 옵션이 아닙니다.");
		};
	}

	private WalkRouteProfile resolveManualWheelchair(RouteOption routeOption) {
		return switch (routeOption) {
			case SAFE -> WalkRouteProfile.WHEELCHAIR_MANUAL_SAFE;
			case SHORTEST -> WalkRouteProfile.WHEELCHAIR_MANUAL_FAST;
			default -> throw invalidRequest("도보 경로 옵션이 아닙니다.");
		};
	}

	private WalkRouteProfile resolveOtherMobility(RouteOption routeOption) {
		return switch (routeOption) {
			case SAFE -> WalkRouteProfile.PEDESTRIAN_SAFE;
			case SHORTEST -> WalkRouteProfile.PEDESTRIAN_FAST;
			default -> throw invalidRequest("도보 경로 옵션이 아닙니다.");
		};
	}

	private RouteException invalidRequest(String message) {
		return new RouteException(RouteErrorCode.INVALID_ROUTE_REQUEST, message);
	}
}
