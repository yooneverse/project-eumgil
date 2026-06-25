package com.ssafy.e102.domain.admin.entity;

import java.time.LocalDateTime;
import java.time.Duration;

import com.ssafy.e102.domain.admin.dto.response.AdminRoutingApplyStatus;
import com.ssafy.e102.global.external.graphhopper.GraphHopperAdminClient.GraphHopperReloadResult;
import com.ssafy.e102.global.external.graphhopper.GraphHopperAdminClient.GraphHopperReloadStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "routing_apply_states")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoutingApplyState {

	public static final String STATE_KEY = "ROUTING_OVERRIDES";

	@Id
	@Column(name = "state_key", nullable = false, updatable = false, length = 60)
	private String stateKey;

	@Column(name = "dirty", nullable = false)
	private boolean dirty;

	@Column(name = "applying", nullable = false)
	private boolean applying;

	@Column(name = "applying_started_at")
	private LocalDateTime applyingStartedAt;

	@Column(name = "dirty_marked_at")
	private LocalDateTime dirtyMarkedAt;

	@Column(name = "last_applied_at")
	private LocalDateTime lastAppliedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "last_result_status", length = 30)
	private AdminRoutingApplyStatus lastResultStatus;

	@Column(name = "last_result_message", columnDefinition = "TEXT")
	private String lastResultMessage;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public static RoutingApplyState initialize() {
		RoutingApplyState state = new RoutingApplyState();
		state.stateKey = STATE_KEY;
		state.dirty = false;
		state.applying = false;
		state.lastResultStatus = AdminRoutingApplyStatus.SKIPPED;
		state.lastResultMessage = "아직 반영할 경로 변경이 없습니다.";
		state.updatedAt = LocalDateTime.now();
		return state;
	}

	public void markDirty(LocalDateTime now) {
		dirty = true;
		dirtyMarkedAt = now;
		updatedAt = now;
		if (!applying) {
			lastResultStatus = AdminRoutingApplyStatus.PENDING;
			lastResultMessage = "DB 저장이 완료되었습니다. 경로 반영이 필요합니다.";
		}
	}

	public void startApplying(LocalDateTime now) {
		applying = true;
		applyingStartedAt = now;
		updatedAt = now;
	}

	public boolean isApplyingStale(LocalDateTime now, Duration staleThreshold) {
		if (!applying || applyingStartedAt == null) {
			return false;
		}
		return !applyingStartedAt.isAfter(now.minus(staleThreshold));
	}

	public void recoverStaleApplyingLock(LocalDateTime now) {
		applying = false;
		applyingStartedAt = null;
		updatedAt = now;
		if (dirty) {
			lastResultStatus = AdminRoutingApplyStatus.PENDING;
			lastResultMessage = "이전 경로 반영 작업이 비정상 종료되어 다시 경로 반영이 필요합니다.";
		}
	}

	public void finishApplying(GraphHopperReloadResult result, LocalDateTime now, LocalDateTime applyStartedDirtyMarkedAt) {
		applying = false;
		applyingStartedAt = null;
		updatedAt = now;
		lastResultStatus = toAdminRoutingApplyStatus(result.status());
		lastResultMessage = result.message();
		if (result.status() == GraphHopperReloadStatus.FAILED) {
			dirty = true;
			return;
		}
		lastAppliedAt = now;
		if (dirtyMarkedAt != null && applyStartedDirtyMarkedAt != null && dirtyMarkedAt.isAfter(applyStartedDirtyMarkedAt)) {
			dirty = true;
			lastResultStatus = AdminRoutingApplyStatus.PENDING;
			lastResultMessage = "반영 중 새 변경이 저장되어 다시 경로 반영이 필요합니다.";
			return;
		}
		dirty = false;
		dirtyMarkedAt = null;
	}

	private AdminRoutingApplyStatus toAdminRoutingApplyStatus(GraphHopperReloadStatus status) {
		return switch (status) {
			case SKIPPED -> AdminRoutingApplyStatus.SKIPPED;
			case APPLIED -> AdminRoutingApplyStatus.APPLIED;
			case APPLIED_WITH_WARNING -> AdminRoutingApplyStatus.APPLIED_WITH_WARNING;
			case FAILED -> AdminRoutingApplyStatus.FAILED;
		};
	}
}
