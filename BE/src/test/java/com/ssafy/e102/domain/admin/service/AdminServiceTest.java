package com.ssafy.e102.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ssafy.e102.domain.admin.dto.response.AdminMeResponse;
import com.ssafy.e102.domain.admin.repository.AdminAreaAssignmentRepository;
import com.ssafy.e102.domain.admin.repository.AdminAreaRepository;
import com.ssafy.e102.domain.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private AdminAreaRepository adminAreaRepository;

	@Mock
	private AdminAreaAssignmentRepository adminAreaAssignmentRepository;

	@Mock
	private AdminAuditLogService adminAuditLogService;

	@Test
	@DisplayName("관리자 principal은 관리자 permission을 조회할 수 있다")
	void getAdminMe() {
		AdminService adminService = new AdminService(userRepository, adminAreaRepository,
			adminAreaAssignmentRepository, adminAuditLogService);
		UUID userId = UUID.randomUUID();

		AdminMeResponse response = adminService.getMe(userId);

		assertThat(response.userId()).isEqualTo(userId);
		assertThat(response.role()).isEqualTo("ADMIN");
		assertThat(response.permissions()).containsExactly(
			"ADMIN_MAP_READ",
			"ADMIN_USER_READ",
			"ADMIN_USER_WRITE",
			"ADMIN_AREA_ASSIGNMENT_READ",
			"ADMIN_AREA_ASSIGNMENT_WRITE",
			"ADMIN_AUDIT_LOG_READ",
			"ADMIN_PLACE_READ",
			"ADMIN_PLACE_WRITE",
			"ADMIN_ROUTE_TUNING_READ",
			"HAZARD_REPORT_READ",
			"HAZARD_REPORT_REVIEW");
	}
}
