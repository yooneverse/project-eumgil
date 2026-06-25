package com.ssafy.e102.domain.route.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.stereotype.Service;

/**
 * route service package의 Spring bean 명명 규칙을 고정한다.
 */
class RouteServiceNamingConventionTest {

	private static final String ROUTE_SERVICE_PACKAGE = "com.ssafy.e102.domain.route.service";

	@Test
	void springServiceBeansEndWithService() {
		Set<Class<?>> serviceBeans = scanServiceBeans();

		assertThat(serviceBeans)
			.isNotEmpty()
			.allSatisfy(type -> assertThat(type.getSimpleName()).endsWith("Service"));
	}

	@Test
	void serviceNamedClassesAreAnnotatedWithService() {
		Set<Class<?>> serviceNamedClasses = scanAllRouteServicePackageClasses()
			.stream()
			.filter(type -> type.getSimpleName().endsWith("Service"))
			.collect(Collectors.toSet());

		assertThat(serviceNamedClasses)
			.isNotEmpty()
			.allSatisfy(type -> assertThat(type).hasAnnotation(Service.class));
	}

	private Set<Class<?>> scanServiceBeans() {
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AnnotationTypeFilter(Service.class));
		return scan(scanner);
	}

	private Set<Class<?>> scanAllRouteServicePackageClasses() {
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new RegexPatternTypeFilter(java.util.regex.Pattern.compile(".*")));
		return scan(scanner);
	}

	private Set<Class<?>> scan(ClassPathScanningCandidateComponentProvider scanner) {
		return scanner.findCandidateComponents(ROUTE_SERVICE_PACKAGE)
			.stream()
			.map(beanDefinition -> loadClass(beanDefinition.getBeanClassName()))
			.collect(Collectors.toSet());
	}

	private Class<?> loadClass(String className) {
		try {
			return Class.forName(className);
		} catch (ClassNotFoundException exception) {
			throw new IllegalStateException("route service class scan failed: " + className, exception);
		}
	}
}
