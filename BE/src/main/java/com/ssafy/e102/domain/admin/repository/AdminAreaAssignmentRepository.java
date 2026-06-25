package com.ssafy.e102.domain.admin.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.e102.domain.admin.entity.AdminAreaAssignment;
import com.ssafy.e102.domain.admin.type.AdminAreaAssignmentType;
import com.ssafy.e102.domain.admin.type.AdminAreaWorkStatus;

public interface AdminAreaAssignmentRepository extends JpaRepository<AdminAreaAssignment, Long> {

	@EntityGraph(attributePaths = "assignee")
	List<AdminAreaAssignment> findAllByOrderByGuAscDongAscAssignmentTypeAsc();

	@EntityGraph(attributePaths = "assignee")
	Optional<AdminAreaAssignment> findByGuAndDongAndAssignmentType(
		String gu,
		String dong,
		AdminAreaAssignmentType assignmentType);

	boolean existsByAssignee_UserIdAndGuAndDongAndAssignmentType(
		UUID assigneeUserId,
		String gu,
		String dong,
		AdminAreaAssignmentType assignmentType);

	List<AdminAreaAssignment> findAllByAssignee_UserId(UUID assigneeUserId);

	@Query("""
		select count(assignment)
		from AdminAreaAssignment assignment
		where assignment.assignmentType = :assignmentType
			and assignment.dong = '전체'
		""")
	long countByAssignmentType(
		@Param("assignmentType")
		AdminAreaAssignmentType assignmentType);

	@Query("""
		select count(assignment)
		from AdminAreaAssignment assignment
		where assignment.assignmentType = :assignmentType
			and assignment.status = :status
			and assignment.dong = '전체'
		""")
	long countByAssignmentTypeAndStatus(
		@Param("assignmentType")
		AdminAreaAssignmentType assignmentType,
		@Param("status")
		AdminAreaWorkStatus status);
}
