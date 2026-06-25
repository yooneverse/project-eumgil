package com.ssafy.e102.domain.admin.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ssafy.e102.domain.admin.dto.response.AdminMeResponse;
import com.ssafy.e102.domain.admin.service.AdminService;
import com.ssafy.e102.global.security.principal.AuthPrincipal;

class AdminControllerTest {

	@Mock
	private AdminService adminService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		mockMvc = MockMvcBuilders.standaloneSetup(new AdminController(adminService))
			.setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
			.build();
	}

	@Test
	@DisplayName("관리자 principal 조회는 현재 사용자의 권한과 permission을 반환한다")
	void getMe() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);
		when(adminService.getMe(userId))
			.thenReturn(new AdminMeResponse(userId, "ADMIN", List.of("HAZARD_REPORT_READ")));

		mockMvc.perform(get("/admin/me")
			.principal(authentication))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("S2000"))
			.andExpect(jsonPath("$.data.userId").value(userId.toString()))
			.andExpect(jsonPath("$.data.role").value("ADMIN"))
			.andExpect(jsonPath("$.data.permissions[0]").value("HAZARD_REPORT_READ"));

		verify(adminService).getMe(userId);
		SecurityContextHolder.clearContext();
	}

	private UsernamePasswordAuthenticationToken authentication(UUID userId) {
		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
			new AuthPrincipal(userId, "access-token"), null);
		SecurityContextHolder.getContext().setAuthentication(authentication);
		return authentication;
	}
}
