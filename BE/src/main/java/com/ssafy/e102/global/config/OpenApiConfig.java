package com.ssafy.e102.global.config;

import java.util.List;

import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;

@Configuration
public class OpenApiConfig {

	private static final String BEARER_AUTH = "bearerAuth";
	private static final DomainTag AUTH_TAG = new DomainTag("/auth", "인증", "로그인, 회원가입, 토큰 재발급, 로그아웃 API");
	private static final DomainTag USER_TAG = new DomainTag("/users", "사용자", "내 정보와 사용자 유형 API");
	private static final DomainTag PLACE_TAG = new DomainTag("/places", "장소", "장소 검색, 지도 마커, 시설 상세, 음성 장소 분석 API");
	private static final DomainTag VOICE_TAG = new DomainTag("/voice", "장소", "음성 장소 분석 API");
	private static final DomainTag ROUTE_TAG = new DomainTag("/routes", "길안내", "경로 탐색과 안내 세션 API");
	private static final DomainTag ROUTE_RATING_TAG = new DomainTag("/route-ratings", "경로 평가", "경로 안내 결과 평가 API");
	private static final DomainTag PLACE_BOOKMARK_TAG = new DomainTag("/bookmarks", "장소 북마크", "장소 북마크 저장, 조회, 삭제 API");
	private static final DomainTag FAVORITE_ROUTE_TAG = new DomainTag("/favorite-routes", "경로 북마크",
		"자주 가는 길 저장, 조회, 수정, 삭제 API");
	private static final DomainTag REPORT_MARKER_TAG = new DomainTag("/hazard/markers", "제보 마커", "승인 제보 마커 조회 API");
	private static final DomainTag REPORT_TAG = new DomainTag("/hazard-reports", "제보", "위험 요소 제보 등록과 내 제보 조회 API");
	private static final DomainTag ADMIN_REPORT_TAG = new DomainTag("/admin/hazard-reports", "관리자 제보",
		"관리자 도로 상태 제보 조회 및 처리 API");
	private static final DomainTag ADMIN_MAP_AREA_TAG = new DomainTag("/admin/areas", "관리자 지도",
		"관리자 보행 네트워크 및 편의시설 조회 API");
	private static final DomainTag ADMIN_MAP_ROAD_TAG = new DomainTag("/admin/road-network", "관리자 지도",
		"관리자 보행 네트워크 및 편의시설 조회 API");
	private static final DomainTag ADMIN_MAP_PLACE_TAG = new DomainTag("/admin/places", "관리자 지도",
		"관리자 보행 네트워크 및 편의시설 조회 API");
	private static final DomainTag ADMIN_TAG = new DomainTag("/admin", "관리자", "관리자 인증 주체 및 권한 확인 API");
	private static final DomainTag ETC_TAG = new DomainTag("/", "기타", "분류되지 않은 API");

	private static final List<DomainTag> PATH_DOMAIN_TAGS = List.of(
		ADMIN_REPORT_TAG,
		ADMIN_MAP_AREA_TAG,
		ADMIN_MAP_ROAD_TAG,
		ADMIN_MAP_PLACE_TAG,
		ADMIN_TAG,
		AUTH_TAG,
		USER_TAG,
		PLACE_TAG,
		VOICE_TAG,
		ROUTE_TAG,
		ROUTE_RATING_TAG,
		PLACE_BOOKMARK_TAG,
		FAVORITE_ROUTE_TAG,
		REPORT_MARKER_TAG,
		REPORT_TAG,
		ETC_TAG);

	private static final List<DomainTag> DISPLAY_DOMAIN_TAGS = List.of(
		AUTH_TAG,
		USER_TAG,
		PLACE_TAG,
		ROUTE_TAG,
		ROUTE_RATING_TAG,
		PLACE_BOOKMARK_TAG,
		FAVORITE_ROUTE_TAG,
		REPORT_MARKER_TAG,
		REPORT_TAG,
		ADMIN_REPORT_TAG,
		ADMIN_MAP_AREA_TAG,
		ADMIN_TAG,
		ETC_TAG);

	private final String serverUrl;

	public OpenApiConfig(@Value("${openapi.server-url:}")
	String serverUrl) {
		this.serverUrl = serverUrl;
	}

	@Bean
	public OpenAPI openApi() {
		OpenAPI openApi = new OpenAPI()
			.components(new Components()
				.addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
					.type(SecurityScheme.Type.HTTP)
					.scheme("bearer")
					.bearerFormat("JWT")))
			.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
			.info(new Info()
				.title("E102 API")
				.description("E102 API 문서")
				.version("v1"))
			.tags(DISPLAY_DOMAIN_TAGS.stream()
				.map(DomainTag::toOpenApiTag)
				.toList());
		if (serverUrl != null && !serverUrl.isBlank()) {
			openApi.servers(List.of(new Server().url(serverUrl)));
		}
		return openApi;
	}

	@Bean
	public GlobalOpenApiCustomizer domainTagCustomizer() {
		return openApi -> {
			if (openApi.getPaths() == null) {
				return;
			}

			openApi.getPaths().forEach((path, pathItem) -> tagOperations(path, pathItem, resolveDomainTag(path)));
			List<String> usedTagNames = openApi.getPaths().values().stream()
				.flatMap(pathItem -> pathItem.readOperations().stream())
				.flatMap(operation -> operation.getTags().stream())
				.distinct()
				.toList();
			openApi.setTags(DISPLAY_DOMAIN_TAGS.stream()
				.filter(domainTag -> usedTagNames.contains(domainTag.name()))
				.map(DomainTag::toOpenApiTag)
				.toList());
		};
	}

	@Bean
	public GroupedOpenApi authApi() {
		return groupedApi(AUTH_TAG, "/auth", "/auth/**");
	}

	@Bean
	public GroupedOpenApi userApi() {
		return groupedApi(USER_TAG, "/users", "/users/**");
	}

	@Bean
	public GroupedOpenApi placeApi() {
		return groupedApi(PLACE_TAG, "/places", "/places/**", "/voice", "/voice/**");
	}

	@Bean
	public GroupedOpenApi routeApi() {
		return groupedApi(ROUTE_TAG, "/routes", "/routes/**");
	}

	@Bean
	public GroupedOpenApi routeRatingApi() {
		return groupedApi(ROUTE_RATING_TAG, "/route-ratings", "/route-ratings/**");
	}

	@Bean
	public GroupedOpenApi placeBookmarkApi() {
		return groupedApi(PLACE_BOOKMARK_TAG, "/bookmarks", "/bookmarks/**");
	}

	@Bean
	public GroupedOpenApi favoriteRouteApi() {
		return groupedApi(FAVORITE_ROUTE_TAG, "/favorite-routes", "/favorite-routes/**");
	}

	@Bean
	public GroupedOpenApi reportApi() {
		return groupedApi(REPORT_TAG, "/hazard-reports", "/hazard-reports/**", "/hazard/markers", "/hazard/markers/**",
			"/hazard/**");
	}

	@Bean
	public GroupedOpenApi adminApi() {
		return groupedApi(ADMIN_TAG, "/admin", "/admin/**");
	}

	private static GroupedOpenApi groupedApi(DomainTag domainTag, String... pathsToMatch) {
		return GroupedOpenApi.builder()
			.group(domainTag.name())
			.pathsToMatch(pathsToMatch)
			.build();
	}

	private static void tagOperations(String path, PathItem pathItem, DomainTag domainTag) {
		List<String> tags = List.of(domainTag.name());
		pathItem.readOperations().forEach(operation -> setOperationTag(path, operation, tags));
	}

	private static void setOperationTag(String path, Operation operation, List<String> tags) {
		operation.setTags(tags);
		if (operation.getSummary() == null || operation.getSummary().isBlank()) {
			operation.setSummary(path);
		}
	}

	private static DomainTag resolveDomainTag(String path) {
		return PATH_DOMAIN_TAGS.stream()
			.filter(domainTag -> domainTag.matches(path))
			.findFirst()
			.orElse(ETC_TAG);
	}

	private record DomainTag(String pathPrefix, String name, String description) {

		private boolean matches(String path) {
			return path.equals(pathPrefix) || path.startsWith(pathPrefix + "/");
		}

		private Tag toOpenApiTag() {
			return new Tag()
				.name(name)
				.description(description);
		}
	}
}
