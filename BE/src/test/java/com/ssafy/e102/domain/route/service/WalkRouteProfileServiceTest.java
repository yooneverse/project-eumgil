package com.ssafy.e102.domain.route.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ssafy.e102.domain.route.exception.RouteErrorCode;
import com.ssafy.e102.domain.route.exception.RouteException;
import com.ssafy.e102.domain.route.type.RouteOption;
import com.ssafy.e102.domain.route.type.WalkRouteProfile;
import com.ssafy.e102.domain.user.type.MobilitySubtype;
import com.ssafy.e102.domain.user.type.PrimaryUserType;

class WalkRouteProfileServiceTest {

	private final WalkRouteProfileService service = new WalkRouteProfileService();

	@Test
	@DisplayName("저시력자는 visual profile로 매핑한다")
	void resolveLowVisionProfiles() {
		assertThat(service.resolve(PrimaryUserType.LOW_VISION, null, RouteOption.SAFE))
			.isEqualTo(WalkRouteProfile.VISUAL_SAFE);
		assertThat(service.resolveProfileName(PrimaryUserType.LOW_VISION, null, RouteOption.SHORTEST))
			.isEqualTo("visual_fast");
	}

	@Test
	@DisplayName("전동 휠체어 사용자는 wheelchair auto profile로 매핑한다")
	void resolvePowerWheelchairProfiles() {
		assertThat(service.resolve(
			PrimaryUserType.MOBILITY_IMPAIRED,
			MobilitySubtype.POWER_WHEELCHAIR,
			RouteOption.SAFE))
			.isEqualTo(WalkRouteProfile.WHEELCHAIR_AUTO_SAFE);
		assertThat(service.resolveProfileName(
			PrimaryUserType.MOBILITY_IMPAIRED,
			MobilitySubtype.POWER_WHEELCHAIR,
			RouteOption.SHORTEST))
			.isEqualTo("wheelchair_auto_fast");
	}

	@Test
	@DisplayName("수동 휠체어 사용자는 wheelchair manual profile로 매핑한다")
	void resolveManualWheelchairProfiles() {
		assertThat(service.resolveProfileName(
			PrimaryUserType.MOBILITY_IMPAIRED,
			MobilitySubtype.MANUAL_WHEELCHAIR,
			RouteOption.SAFE))
			.isEqualTo("wheelchair_manual_safe");
		assertThat(service.resolveProfileName(
			PrimaryUserType.MOBILITY_IMPAIRED,
			MobilitySubtype.MANUAL_WHEELCHAIR,
			RouteOption.SHORTEST))
			.isEqualTo("wheelchair_manual_fast");
	}

	@Test
	@DisplayName("기타 보행약자는 일반 보행 profile로 매핑한다")
	void resolveOtherMobilityProfiles() {
		assertThat(service.resolveProfileName(
			PrimaryUserType.MOBILITY_IMPAIRED,
			MobilitySubtype.OTHER_MOBILITY,
			RouteOption.SAFE))
			.isEqualTo("pedestrian_safe");
		assertThat(service.resolveProfileName(
			PrimaryUserType.MOBILITY_IMPAIRED,
			MobilitySubtype.OTHER_MOBILITY,
			RouteOption.SHORTEST))
			.isEqualTo("pedestrian_fast");
	}

	@Test
	@DisplayName("잘못된 사용자 유형 조합은 RT4000으로 거부한다")
	void rejectInvalidUserTypeCombinations() {
		assertInvalidRequest(() -> service.resolve(null, null, RouteOption.SAFE));
		assertInvalidRequest(() -> service.resolve(PrimaryUserType.LOW_VISION, MobilitySubtype.OTHER_MOBILITY,
			RouteOption.SAFE));
		assertInvalidRequest(() -> service.resolve(PrimaryUserType.MOBILITY_IMPAIRED, null, RouteOption.SAFE));
		assertInvalidRequest(() -> service.resolve(PrimaryUserType.LOW_VISION, null, null));
	}

	private void assertInvalidRequest(ThrowingCallable callable) {
		assertThatThrownBy(callable::call)
			.isInstanceOf(RouteException.class)
			.extracting(exception -> ((RouteException)exception).getErrorCode())
			.isEqualTo(RouteErrorCode.INVALID_ROUTE_REQUEST);
	}

	@FunctionalInterface
	private interface ThrowingCallable {
		void call();
	}
}
