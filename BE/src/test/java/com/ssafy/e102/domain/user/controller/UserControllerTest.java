package com.ssafy.e102.domain.user.controller;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ssafy.e102.domain.user.dto.response.UserMeResponse;
import com.ssafy.e102.domain.user.dto.response.UserTypeResponse;
import com.ssafy.e102.domain.user.service.UserService;
import com.ssafy.e102.domain.user.type.MobilitySubtype;
import com.ssafy.e102.domain.user.type.PrimaryUserType;
import com.ssafy.e102.domain.user.type.SocialProvider;
import com.ssafy.e102.global.security.principal.AuthPrincipal;

class UserControllerTest {

	@Mock
	private UserService userService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService))
			.setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
			.build();
	}

	@Test
	@DisplayName("내 정보 조회는 현재 사용자 정보를 반환한다")
	void getMe() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);
		Mockito.when(userService.getMe(userId))
			.thenReturn(new UserMeResponse(
				userId,
				SocialProvider.KAKAO,
				PrimaryUserType.MOBILITY_IMPAIRED,
				MobilitySubtype.MANUAL_WHEELCHAIR));

		mockMvc.perform(get("/users/me")
			.principal(authentication))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("S2000"))
			.andExpect(jsonPath("$.data.userId").value(userId.toString()))
			.andExpect(jsonPath("$.data.socialProvider").value("KAKAO"))
			.andExpect(jsonPath("$.data.selectedPrimaryUserType").value("MOBILITY_IMPAIRED"))
			.andExpect(jsonPath("$.data.selectedMobilitySubtype").value("MANUAL_WHEELCHAIR"));

		verify(userService).getMe(userId);
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("사용자 유형 수정은 저장된 사용자 유형을 반환한다")
	void updateUserType() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);
		Mockito.when(userService.updateUserType(userId, PrimaryUserType.LOW_VISION, null))
			.thenReturn(new UserTypeResponse(userId, PrimaryUserType.LOW_VISION, null));

		mockMvc.perform(patch("/users/me/user-type")
			.principal(authentication)
			.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
			.content("{\"selectedPrimaryUserType\":\"LOW_VISION\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("S2000"))
			.andExpect(jsonPath("$.data.userId").value(userId.toString()))
			.andExpect(jsonPath("$.data.selectedPrimaryUserType").value("LOW_VISION"))
			.andExpect(jsonPath("$.data.selectedMobilitySubtype").doesNotExist());

		verify(userService).updateUserType(userId, PrimaryUserType.LOW_VISION, null);
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("회원탈퇴 요청은 현재 사용자 계정을 삭제하고 성공 메시지를 반환한다")
	void withdraw() throws Exception {
		UUID userId = UUID.randomUUID();
		UsernamePasswordAuthenticationToken authentication = authentication(userId);

		mockMvc.perform(delete("/users/me")
			.principal(authentication))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("S2000"))
			.andExpect(jsonPath("$.data").doesNotExist())
			.andExpect(jsonPath("$.message").value("회원탈퇴가 완료되었습니다."));

		verify(userService).withdraw(userId, "access-token");
		SecurityContextHolder.clearContext();
	}

	private UsernamePasswordAuthenticationToken authentication(UUID userId) {
		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
			new AuthPrincipal(userId, "access-token"), null);
		SecurityContextHolder.getContext().setAuthentication(authentication);
		return authentication;
	}
}
