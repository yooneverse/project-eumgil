package com.ssafy.e102.domain.admin.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.e102.domain.admin.dto.response.AdminRoutingApplyStateResponse;
import com.ssafy.e102.domain.admin.dto.response.AdminRoutingApplyStatus;
import com.ssafy.e102.domain.admin.entity.RoutingApplyState;
import com.ssafy.e102.domain.admin.repository.RoutingApplyStateRepository;
import com.ssafy.e102.domain.report.repository.HazardReportRouteReviewRepository;
import com.ssafy.e102.global.exception.BusinessException;
import com.ssafy.e102.global.exception.CommonErrorCode;
import com.ssafy.e102.global.external.graphhopper.GraphHopperAdminClient;
import com.ssafy.e102.global.external.graphhopper.GraphHopperAdminClient.GraphHopperReloadResult;
import com.ssafy.e102.global.external.graphhopper.GraphHopperAdminClient.GraphHopperReloadStatus;

@Service
public class AdminRoutingApplyService {

	private final RoutingApplyStateRepository routingApplyStateRepository;
	private final HazardReportRouteReviewRepository hazardReportRouteReviewRepository;
	private final GraphHopperAdminClient graphHopperAdminClient;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;
	private final Duration staleApplyingLockThreshold;

	public AdminRoutingApplyService(
		RoutingApplyStateRepository routingApplyStateRepository,
		HazardReportRouteReviewRepository hazardReportRouteReviewRepository,
		GraphHopperAdminClient graphHopperAdminClient,
		PlatformTransactionManager transactionManager,
		Clock clock) {
		this.routingApplyStateRepository = routingApplyStateRepository;
		this.hazardReportRouteReviewRepository = hazardReportRouteReviewRepository;
		this.graphHopperAdminClient = graphHopperAdminClient;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.clock = clock;
		this.staleApplyingLockThreshold = graphHopperAdminClient.staleLockRecoveryThreshold();
	}

	public AdminRoutingApplyStateResponse getCurrentState() {
		RoutingApplyState state = Objects.requireNonNull(transactionTemplate.execute(status -> {
			RoutingApplyState currentState = getOrCreateState(true);
			recoverStaleApplyingLockIfNeeded(currentState);
			routingApplyStateRepository.save(currentState);
			return currentState;
		}));
		return toResponse(state);
	}

	public void markDirtyInCurrentTransaction() {
		RoutingApplyState state = getOrCreateState(true);
		state.markDirty(LocalDateTime.now(clock));
		routingApplyStateRepository.save(state);
	}

	public AdminRoutingApplyStateResponse applyRoutingOverrides() {
		ApplyStart applyStart = Objects.requireNonNull(transactionTemplate.execute(status -> {
			RoutingApplyState state = getOrCreateState(true);
			recoverStaleApplyingLockIfNeeded(state);
			if (state.isApplying()) {
				throw new BusinessException(CommonErrorCode.CONFLICT, "이미 다른 관리자가 경로 반영을 실행 중입니다.");
			}
			if (!state.isDirty()) {
				return ApplyStart.skipped(toResponse(
					AdminRoutingApplyStatus.SKIPPED,
					"반영할 최신 경로 변경이 없습니다.",
					state));
			}
			LocalDateTime now = LocalDateTime.now(clock);
			LocalDateTime dirtyMarkedAt = state.getDirtyMarkedAt();
			state.startApplying(now);
			routingApplyStateRepository.save(state);
			return ApplyStart.started(dirtyMarkedAt);
		}));
		if (applyStart.skippedResponse() != null) {
			return applyStart.skippedResponse();
		}
		GraphHopperReloadResult reloadResult = reloadRoutingOverridesSafely();
		return Objects.requireNonNull(transactionTemplate.execute(status -> {
			RoutingApplyState state = getOrCreateState(true);
			state.finishApplying(reloadResult, LocalDateTime.now(clock), applyStart.dirtyMarkedAt());
			routingApplyStateRepository.save(state);
			AdminRoutingApplyStateResponse response = toResponse(state);
			updateCompletedHazardRouteReviewApplyStatuses(
				toAdminRoutingApplyStatus(reloadResult.status()),
				reloadResult.message(),
				response.lastAppliedAt(),
				applyStart.dirtyMarkedAt());
			return response;
		}));
	}

	private void updateCompletedHazardRouteReviewApplyStatuses(
		AdminRoutingApplyStatus reloadStatus,
		String message,
		LocalDateTime appliedAt,
		LocalDateTime appliedThrough) {
		if (appliedThrough == null) {
			return;
		}
		if (reloadStatus == AdminRoutingApplyStatus.FAILED) {
			hazardReportRouteReviewRepository.updateRoutingApplyStatusForCompletedBefore(
				List.of(AdminRoutingApplyStatus.PENDING),
				AdminRoutingApplyStatus.FAILED,
				message,
				null,
				appliedThrough);
			return;
		}
		if (reloadStatus == AdminRoutingApplyStatus.APPLIED
			|| reloadStatus == AdminRoutingApplyStatus.APPLIED_WITH_WARNING
			|| reloadStatus == AdminRoutingApplyStatus.SKIPPED) {
			hazardReportRouteReviewRepository.updateRoutingApplyStatusForCompletedBefore(
				List.of(AdminRoutingApplyStatus.PENDING, AdminRoutingApplyStatus.FAILED),
				reloadStatus,
				message,
				appliedAt,
				appliedThrough);
		}
	}

	private AdminRoutingApplyStatus toAdminRoutingApplyStatus(GraphHopperReloadStatus status) {
		return switch (status) {
			case SKIPPED -> AdminRoutingApplyStatus.SKIPPED;
			case APPLIED -> AdminRoutingApplyStatus.APPLIED;
			case APPLIED_WITH_WARNING -> AdminRoutingApplyStatus.APPLIED_WITH_WARNING;
			case FAILED -> AdminRoutingApplyStatus.FAILED;
		};
	}

	private RoutingApplyState getOrCreateState(boolean forUpdate) {
		return (forUpdate
			? routingApplyStateRepository.findForUpdate(RoutingApplyState.STATE_KEY)
			: routingApplyStateRepository.findById(RoutingApplyState.STATE_KEY))
			.orElseGet(() -> routingApplyStateRepository.save(RoutingApplyState.initialize()));
	}

	private GraphHopperReloadResult reloadRoutingOverridesSafely() {
		try {
			return graphHopperAdminClient.reloadRoutingOverrides();
		} catch (RuntimeException exception) {
			return new GraphHopperReloadResult(
				GraphHopperReloadStatus.FAILED,
				exception.getMessage() == null ? "GraphHopper reload 호출에 실패했습니다." : exception.getMessage());
		}
	}

	private void recoverStaleApplyingLockIfNeeded(RoutingApplyState state) {
		LocalDateTime now = LocalDateTime.now(clock);
		if (state.isApplyingStale(now, staleApplyingLockThreshold)) {
			state.recoverStaleApplyingLock(now);
		}
	}

	private AdminRoutingApplyStateResponse toResponse(RoutingApplyState state) {
		return toResponse(
			state.isApplying() ? AdminRoutingApplyStatus.PENDING : state.getLastResultStatus(),
			state.getLastResultMessage(),
			state);
	}

	private AdminRoutingApplyStateResponse toResponse(
		AdminRoutingApplyStatus status,
		String message,
		RoutingApplyState state) {
		return new AdminRoutingApplyStateResponse(
			status,
			message,
			state.isDirty(),
			state.isApplying(),
			state.getLastAppliedAt());
	}

	private record ApplyStart(LocalDateTime dirtyMarkedAt, AdminRoutingApplyStateResponse skippedResponse) {

		private static ApplyStart started(LocalDateTime dirtyMarkedAt) {
			return new ApplyStart(dirtyMarkedAt, null);
		}

		private static ApplyStart skipped(AdminRoutingApplyStateResponse response) {
			return new ApplyStart(null, response);
		}
	}
}
