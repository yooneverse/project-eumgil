package com.ssafy.e102.domain.route.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

class RouteOpenApiDocumentationTest {

	@Test
	void routeApisExposeKoreanSwaggerSummaries() {
		assertTag(RouteController.class, "길안내");
		assertOperation(RouteController.class, "searchWalkRoutes", "도보 경로 검색");
		assertOperation(RouteController.class, "searchTransitRoutes", "대중교통 경로 검색");
		assertOperation(RouteController.class, "reroute", "경로 재탐색");
		assertOperation(RouteController.class, "selectRoute", "안내 경로 선택");
		assertOperation(RouteController.class, "endRoute", "안내 종료");
		assertOperation(RouteController.class, "refreshTransit", "대중교통 도착정보 갱신");
	}

	@Test
	void routeRatingApiExposesKoreanSwaggerSummary() {
		assertTag(RouteRatingController.class, "경로 평가");
		assertOperation(RouteRatingController.class, "rateRoute", "경로 평가 등록");
	}

	private void assertTag(Class<?> controllerType, String expectedName) {
		Tag tag = controllerType.getAnnotation(Tag.class);

		assertThat(tag).isNotNull();
		assertThat(tag.name()).isEqualTo(expectedName);
		assertThat(tag.description()).isNotBlank();
	}

	private void assertOperation(Class<?> controllerType, String methodName, String expectedSummary) {
		Operation operation = findMethod(controllerType, methodName).getAnnotation(Operation.class);

		assertThat(operation).isNotNull();
		assertThat(operation.summary()).isEqualTo(expectedSummary);
		assertThat(operation.description()).isNotBlank();
	}

	private Method findMethod(Class<?> controllerType, String methodName) {
		return Arrays.stream(controllerType.getDeclaredMethods())
			.filter(method -> method.getName().equals(methodName))
			.findFirst()
			.orElseThrow();
	}
}
