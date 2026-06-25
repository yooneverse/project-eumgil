package com.ssafy.e102.domain.admin.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.e102.domain.admin.dto.request.AdminAreaAssignmentStatusUpdateRequest;
import com.ssafy.e102.domain.admin.dto.request.AdminAreaAssignmentUpsertRequest;
import com.ssafy.e102.domain.admin.dto.request.AdminUserRoleUpdateRequest;
import com.ssafy.e102.domain.admin.dto.response.AdminAreaAssignmentListResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminAreaAssignmentResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminAuditLogListResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminMeResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminUserListResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminUserResponse;
import com.ssafy.e102.domain.admin.entity.AdminAreaAssignment;
import com.ssafy.e102.domain.admin.repository.AdminAreaAssignmentRepository;
import com.ssafy.e102.domain.admin.repository.AdminAreaRepository;
import com.ssafy.e102.domain.admin.type.AdminAreaAssignmentType;
import com.ssafy.e102.domain.admin.type.AdminAreaWorkStatus;
import com.ssafy.e102.domain.user.entity.User;
import com.ssafy.e102.domain.user.exception.UserErrorCode;
import com.ssafy.e102.domain.user.exception.UserException;
import com.ssafy.e102.domain.user.repository.UserRepository;
import com.ssafy.e102.domain.user.type.UserRole;
import com.ssafy.e102.global.exception.BusinessException;
import com.ssafy.e102.global.exception.CommonErrorCode;

@Service
@Transactional(readOnly = true)
public class AdminService {

	public static final String ALL_DONG = "전체";

	private static final List<String> ADMIN_PERMISSIONS = List.of(
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

	private final UserRepository userRepository;
	private final AdminAreaRepository adminAreaRepository;
	private final AdminAreaAssignmentRepository adminAreaAssignmentRepository;
	private final AdminAuditLogService adminAuditLogService;

	public AdminService(
		UserRepository userRepository,
		AdminAreaRepository adminAreaRepository,
		AdminAreaAssignmentRepository adminAreaAssignmentRepository,
		AdminAuditLogService adminAuditLogService) {
		this.userRepository = userRepository;
		this.adminAreaRepository = adminAreaRepository;
		this.adminAreaAssignmentRepository = adminAreaAssignmentRepository;
		this.adminAuditLogService = adminAuditLogService;
	}

	public AdminMeResponse getMe(UUID userId) {
		return AdminMeResponse.of(userId, ADMIN_PERMISSIONS);
	}

	public AdminUserListResponse getUsers() {
		return new AdminUserListResponse(userRepository.findAllByRoleOrderByCreatedAtDesc(UserRole.ADMIN)
			.stream()
			.map(AdminUserResponse::from)
			.toList());
	}

	@Transactional
	public AdminUserResponse updateUserRole(
		UUID actorUserId,
		UUID targetUserId,
		AdminUserRoleUpdateRequest request) {
		if (actorUserId.equals(targetUserId) && request.role() != UserRole.ADMIN) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, "자기 자신의 관리자 권한은 해제할 수 없습니다.");
		}
		User user = requireUser(targetUserId);
		user.changeRole(request.role());
		return AdminUserResponse.from(user);
	}

	public AdminAreaAssignmentListResponse getAreaAssignments() {
		Map<String, AdminAreaAssignment> assignmentsByArea = adminAreaAssignmentRepository
			.findAllByOrderByGuAscDongAscAssignmentTypeAsc()
			.stream()
			.collect(Collectors.toMap(
				assignment -> areaKey(assignment.getGu(), assignment.getDong(), assignment.getAssignmentType()),
				Function.identity()));

		List<AdminAreaAssignmentResponse> assignments = findSelectableAreas()
			.keySet()
			.stream()
			.flatMap(gu -> Arrays.stream(AdminAreaAssignmentType.values())
				.map(assignmentType -> {
					AdminAreaAssignment assignment = assignmentsByArea
						.get(areaKey(gu, ALL_DONG, assignmentType));
					if (assignment != null) {
						return AdminAreaAssignmentResponse.from(assignment);
					}
					return new AdminAreaAssignmentResponse(
						null,
						gu,
						ALL_DONG,
						assignmentType,
						null,
						null,
						AdminAreaWorkStatus.NOT_STARTED,
						null);
				}))
			.toList();
		return new AdminAreaAssignmentListResponse(assignments);
	}

	@Transactional
	public AdminAreaAssignmentResponse upsertAreaAssignment(AdminAreaAssignmentUpsertRequest request) {
		validateGu(request.gu());
		String dong = ALL_DONG;
		User assignee = request.assigneeUserId() == null ? null : requireAdminUser(request.assigneeUserId());
		AdminAreaAssignment assignment = adminAreaAssignmentRepository
			.findByGuAndDongAndAssignmentType(request.gu(), dong, request.assignmentType())
			.orElse(null);
		if (assignment == null) {
			assignment = adminAreaAssignmentRepository.save(AdminAreaAssignment.create(
				request.gu(),
				dong,
				request.assignmentType(),
				null,
				AdminAreaWorkStatus.NOT_STARTED));
		}
		assignment.assign(assignee);
		if (request.status() != null) {
			assignment.changeStatus(request.status());
		}
		return AdminAreaAssignmentResponse.from(assignment);
	}

	@Transactional
	public AdminAreaAssignmentResponse updateAreaAssignmentStatus(
		Long assignmentId,
		AdminAreaAssignmentStatusUpdateRequest request) {
		AdminAreaAssignment assignment = adminAreaAssignmentRepository.findById(assignmentId)
			.orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "담당 구를 찾을 수 없습니다."));
		assignment.changeStatus(request.status());
		return AdminAreaAssignmentResponse.from(assignment);
	}

	public AdminAuditLogListResponse getAuditLogs(
		Long cursor,
		String action,
		String gu,
		String dong,
		UUID actorUserId,
		int size) {
		return adminAuditLogService.getLogs(cursor, action, gu, dong, actorUserId, size);
	}

	public void requireCanEditArea(UUID userId, String gu, String dong, AdminAreaAssignmentType assignmentType) {
		if (gu == null || gu.isBlank()) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, "수정할 구는 필수입니다.");
		}
		if (assignmentType == null) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, "담당 유형은 필수입니다.");
		}
		if (!adminAreaAssignmentRepository.existsByAssignee_UserIdAndGuAndDongAndAssignmentType(
			userId,
			gu,
			ALL_DONG,
			assignmentType)) {
			throw new BusinessException(CommonErrorCode.FORBIDDEN, "담당 구만 수정할 수 있습니다.");
		}
	}

	private User requireUser(UUID userId) {
		return userRepository.findById(userId)
			.orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
	}

	private User requireAdminUser(UUID userId) {
		User user = requireUser(userId);
		if (user.getRole() != UserRole.ADMIN) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, "담당자는 ADMIN 권한 사용자여야 합니다.");
		}
		return user;
	}

	private void validateGu(String gu) {
		if (!adminAreaRepository.existsGu(gu)) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, "존재하지 않는 구입니다.");
		}
	}

	private Map<String, Set<String>> findSelectableAreas() {
		Map<String, Set<String>> dongsByGu = new TreeMap<>();
		adminAreaRepository.findDistinctAreas()
			.forEach(row -> {
				String gu = (String)row[0];
				String dong = (String)row[1];
				dongsByGu.computeIfAbsent(gu, ignored -> new TreeSet<>())
					.add(dong);
			});
		dongsByGu.values()
			.forEach(dongs -> {
				Set<String> syntheticDongs = dongs.stream()
					.map(this::toSyntheticDong)
					.filter(dong -> !dongs.contains(dong))
					.collect(Collectors.toCollection(TreeSet::new));
				dongs.addAll(syntheticDongs);
			});
		return dongsByGu;
	}

	private String toSyntheticDong(String dong) {
		return dong.replace("1동", "동")
			.replace("2동", "동")
			.replace("3동", "동")
			.replace("4동", "동");
	}

	private String areaKey(String gu, String dong, AdminAreaAssignmentType assignmentType) {
		return gu + "\n" + dong + "\n" + assignmentType;
	}
}
