package com.ssafy.e102.global.test.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthTestEndpointAccessFilterTest {

	@Test
	@DisplayName("테스트 페이지가 비활성화되면 auth-test.html 요청을 404로 막는다")
	void disabledAuthTestPageReturnsNotFound() throws Exception {
		AuthTestEndpointAccessFilter filter = new AuthTestEndpointAccessFilter(disabledProperties());
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth-test.html");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain filterChain = new MockFilterChain();

		filter.doFilter(request, response, filterChain);

		assertThat(response.getStatus()).isEqualTo(404);
	}

	@Test
	@DisplayName("테스트 페이지가 비활성화되면 test-config 요청을 404로 막는다")
	void disabledAuthTestConfigReturnsNotFound() throws Exception {
		AuthTestEndpointAccessFilter filter = new AuthTestEndpointAccessFilter(disabledProperties());
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/test-config");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain filterChain = new MockFilterChain();

		filter.doFilter(request, response, filterChain);

		assertThat(response.getStatus()).isEqualTo(404);
	}

	@Test
	@DisplayName("테스트 페이지가 활성화되면 요청을 다음 필터로 넘긴다")
	void enabledAuthTestPagePassesThrough() throws Exception {
		AuthTestEndpointAccessFilter filter = new AuthTestEndpointAccessFilter(enabledProperties());
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth-test.html");
		MockHttpServletResponse response = new MockHttpServletResponse();
		jakarta.servlet.FilterChain filterChain = org.mockito.Mockito.mock(jakarta.servlet.FilterChain.class);

		filter.doFilter(request, response, filterChain);

		verify(filterChain).doFilter(request, response);
	}

	@Test
	@DisplayName("테스트 페이지 경로가 아니면 비활성화 상태여도 요청을 통과시킨다")
	void unrelatedPathPassesThrough() throws Exception {
		AuthTestEndpointAccessFilter filter = new AuthTestEndpointAccessFilter(disabledProperties());
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/social-login");
		MockHttpServletResponse response = new MockHttpServletResponse();
		jakarta.servlet.FilterChain filterChain = org.mockito.Mockito.mock(jakarta.servlet.FilterChain.class);

		filter.doFilter(request, response, filterChain);

		verify(filterChain).doFilter(request, response);
		assertThat(response.getStatus()).isEqualTo(200);
	}

	private AuthTestConfigProperties enabledProperties() {
		return new AuthTestConfigProperties(true, "kakao-js-key", "naver-client-id", "google-client-id");
	}

	private AuthTestConfigProperties disabledProperties() {
		return new AuthTestConfigProperties(false, "kakao-js-key", "naver-client-id", "google-client-id");
	}
}
