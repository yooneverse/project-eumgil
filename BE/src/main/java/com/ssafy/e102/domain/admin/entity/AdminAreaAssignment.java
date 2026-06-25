package com.ssafy.e102.domain.admin.entity;

import com.ssafy.e102.domain.admin.type.AdminAreaWorkStatus;
import com.ssafy.e102.domain.admin.type.AdminAreaAssignmentType;
import com.ssafy.e102.domain.user.entity.User;
import com.ssafy.e102.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "admin_area_assignments", indexes = {
	@Index(name = "idx_admin_area_assignments_assignee", columnList = "assignee_user_id"),
	@Index(name = "idx_admin_area_assignments_status", columnList = "status"),
	@Index(name = "idx_admin_area_assignments_type_status", columnList = "assignment_type,status")
}, uniqueConstraints = {
	@UniqueConstraint(name = "uk_admin_area_assignments_area_type", columnNames = {"gu", "dong", "assignment_type"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAreaAssignment extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "assignment_id", nullable = false, updatable = false)
	private Long assignmentId;

	@Column(name = "gu", nullable = false, length = 50)
	private String gu;

	@Column(name = "dong", nullable = false, length = 50)
	private String dong;

	@Enumerated(EnumType.STRING)
	@Column(name = "assignment_type", nullable = false, length = 30)
	private AdminAreaAssignmentType assignmentType;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assignee_user_id")
	private User assignee;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 30)
	private AdminAreaWorkStatus status;

	public static AdminAreaAssignment create(
		String gu,
		String dong,
		AdminAreaAssignmentType assignmentType,
		User assignee,
		AdminAreaWorkStatus status) {
		AdminAreaAssignment assignment = new AdminAreaAssignment();
		assignment.gu = requireText(gu, "구는 필수입니다.");
		assignment.dong = requireText(dong, "동은 필수입니다.");
		assignment.assignmentType = requireAssignmentType(assignmentType);
		assignment.assignee = assignee;
		assignment.status = status == null ? AdminAreaWorkStatus.NOT_STARTED : status;
		return assignment;
	}

	public void assign(User assignee) {
		this.assignee = assignee;
	}

	public void changeStatus(AdminAreaWorkStatus status) {
		if (status == null) {
			throw new IllegalArgumentException("작업 상태는 필수입니다.");
		}
		this.status = status;
	}

	private static String requireText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return value.trim();
	}

	private static AdminAreaAssignmentType requireAssignmentType(AdminAreaAssignmentType assignmentType) {
		if (assignmentType == null) {
			throw new IllegalArgumentException("담당 유형은 필수입니다.");
		}
		return assignmentType;
	}
}
