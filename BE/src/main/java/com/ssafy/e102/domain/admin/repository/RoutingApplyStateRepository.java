package com.ssafy.e102.domain.admin.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.e102.domain.admin.entity.RoutingApplyState;

import jakarta.persistence.LockModeType;

public interface RoutingApplyStateRepository extends JpaRepository<RoutingApplyState, String> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select state from RoutingApplyState state where state.stateKey = :stateKey")
	Optional<RoutingApplyState> findForUpdate(@Param("stateKey") String stateKey);
}
