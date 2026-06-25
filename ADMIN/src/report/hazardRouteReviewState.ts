import type {
  AdminHazardRouteReview,
  AdminHazardRouteReviewIntent,
  AdminRoadSegmentAttributesUpdateRequest,
  AdminRoutingApplyStateResponse,
  AdminRoutingApplyStatus,
  HazardReportStatus,
  UpdateAdminHazardRouteReviewRequest,
} from "../types";

const hazardRouteReviewStoragePrefix = "busan-eumgil-ADMIN:hazard-route-review:";

export type HazardRouteReviewIntent = "approve" | "restore";
export type HazardRouteReviewStage = "IN_PROGRESS" | "COMPLETED";
export type HazardRouteReviewTone = "blue" | "orange" | "green" | "red" | "purple" | "gray";
export type HazardDisplayStatusKey = "PENDING" | "IN_PROGRESS" | "COMPLETED" | "REJECTED" | "RESTORE_PENDING" | "RESTORED";

export interface HazardRouteReviewRecord {
  reportId: number;
  intent: HazardRouteReviewIntent;
  stage: HazardRouteReviewStage;
  reviewerUserId: string;
  gu: string | null;
  dong: string | null;
  startedAt: string;
  updatedAt: string;
  completedAt: string | null;
  selectedSegmentEdgeId: string | null;
  segmentDrafts: Record<string, AdminRoadSegmentAttributesUpdateRequest>;
  routingApplyStatus?: AdminRoutingApplyStatus | null;
  routingApplyMessage?: string | null;
}

export interface HazardDisplayStatus {
  key: HazardDisplayStatusKey;
  label: string;
  tone: HazardRouteReviewTone;
}

export interface HazardOperationStatus {
  label: string;
  tone: HazardRouteReviewTone;
}

export function hazardRouteReviewStorageKey(reportId: number) {
  return `${hazardRouteReviewStoragePrefix}${reportId}`;
}

export function loadStoredHazardRouteReview(reportId: number): HazardRouteReviewRecord | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = window.localStorage.getItem(hazardRouteReviewStorageKey(reportId));
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<HazardRouteReviewRecord> | null;
    if (!parsed || typeof parsed !== "object") {
      return null;
    }
    if (typeof parsed.reportId !== "number" || (parsed.intent !== "approve" && parsed.intent !== "restore")) {
      return null;
    }
    return {
      reportId: parsed.reportId,
      intent: parsed.intent,
      stage: parsed.stage === "COMPLETED" ? "COMPLETED" : "IN_PROGRESS",
      reviewerUserId: typeof parsed.reviewerUserId === "string" ? parsed.reviewerUserId : "ADMIN",
      gu: typeof parsed.gu === "string" ? parsed.gu : null,
      dong: typeof parsed.dong === "string" ? parsed.dong : null,
      startedAt: typeof parsed.startedAt === "string" ? parsed.startedAt : new Date().toISOString(),
      updatedAt: typeof parsed.updatedAt === "string"
        ? parsed.updatedAt
        : typeof parsed.startedAt === "string"
          ? parsed.startedAt
          : new Date().toISOString(),
      completedAt: typeof parsed.completedAt === "string" ? parsed.completedAt : null,
      selectedSegmentEdgeId: typeof parsed.selectedSegmentEdgeId === "string" ? parsed.selectedSegmentEdgeId : null,
      segmentDrafts: parsed.segmentDrafts && typeof parsed.segmentDrafts === "object" ? parsed.segmentDrafts : {},
      routingApplyStatus: isAdminRoutingApplyStatus(parsed.routingApplyStatus) ? parsed.routingApplyStatus : null,
      routingApplyMessage: typeof parsed.routingApplyMessage === "string" ? parsed.routingApplyMessage : null,
    };
  } catch {
    return null;
  }
}

export function storeHazardRouteReview(review: HazardRouteReviewRecord | null) {
  if (typeof window === "undefined" || !review) return;
  window.localStorage.setItem(hazardRouteReviewStorageKey(review.reportId), JSON.stringify(review));
}

export function clearStoredHazardRouteReview(reportId: number) {
  if (typeof window === "undefined") return;
  window.localStorage.removeItem(hazardRouteReviewStorageKey(reportId));
}

export function startHazardRouteReview({
  reportId,
  intent,
  reviewerUserId,
  now,
  existing,
}: {
  reportId: number;
  intent: HazardRouteReviewIntent;
  reviewerUserId: string;
  now: string;
  existing?: HazardRouteReviewRecord | null;
}): HazardRouteReviewRecord {
  const canResumeExisting = existing?.intent === intent && existing.stage === "IN_PROGRESS";
  return {
    reportId,
    intent,
    stage: "IN_PROGRESS",
    reviewerUserId,
    gu: existing?.gu ?? null,
    dong: existing?.dong ?? null,
    startedAt: canResumeExisting ? existing.startedAt : now,
    updatedAt: now,
    completedAt: null,
    selectedSegmentEdgeId: existing?.selectedSegmentEdgeId ?? null,
    segmentDrafts: existing?.segmentDrafts ?? {},
    routingApplyStatus: existing?.routingApplyStatus ?? null,
    routingApplyMessage: existing?.routingApplyMessage ?? null,
  };
}

export function hydrateHazardRouteReviewRecord(review?: AdminHazardRouteReview | null): HazardRouteReviewRecord | null {
  if (!review) {
    return null;
  }

  return {
    reportId: review.reportId,
    intent: fromAdminHazardRouteReviewIntent(review.intent),
    stage: review.stage,
    reviewerUserId: review.reviewerUserId,
    gu: review.gu,
    dong: review.dong,
    startedAt: review.startedAt,
    updatedAt: review.updatedAt,
    completedAt: review.completedAt,
    routingApplyStatus: review.routingApplyStatus ?? null,
    routingApplyMessage: review.routingApplyMessage ?? null,
    selectedSegmentEdgeId: review.selectedSegmentEdgeId == null ? null : String(review.selectedSegmentEdgeId),
    segmentDrafts: review.segmentDrafts.reduce<Record<string, AdminRoadSegmentAttributesUpdateRequest>>((drafts, segmentDraft) => {
      drafts[String(segmentDraft.edgeId)] = {
        walkAccess: segmentDraft.walkAccess ?? null,
        brailleBlockState: segmentDraft.brailleBlockState ?? null,
        audioSignalState: segmentDraft.audioSignalState ?? null,
        widthState: segmentDraft.widthState ?? null,
        surfaceState: segmentDraft.surfaceState ?? null,
        stairsState: segmentDraft.stairsState ?? null,
        signalState: segmentDraft.signalState ?? null,
      };
      return drafts;
    }, {}),
  };
}

export function toAdminHazardRouteReviewIntent(intent: HazardRouteReviewIntent): AdminHazardRouteReviewIntent {
  return intent === "restore" ? "RESTORE" : "APPROVE";
}

export function toAdminHazardRouteReviewUpdateRequest(review: HazardRouteReviewRecord): UpdateAdminHazardRouteReviewRequest {
  return {
    selectedSegmentEdgeId: review.selectedSegmentEdgeId == null ? null : Number(review.selectedSegmentEdgeId),
    segmentDrafts: Object.entries(review.segmentDrafts).map(([edgeId, draft]) => ({
      edgeId: Number(edgeId),
      walkAccess: draft.walkAccess ?? null,
      brailleBlockState: draft.brailleBlockState ?? null,
      audioSignalState: draft.audioSignalState ?? null,
      widthState: draft.widthState ?? null,
      surfaceState: draft.surfaceState ?? null,
      stairsState: draft.stairsState ?? null,
      signalState: draft.signalState ?? null,
    })),
  };
}

export function selectHazardRouteReviewSegment(
  review: HazardRouteReviewRecord,
  edgeId: string | number,
  now: string,
) {
  return {
    ...review,
    selectedSegmentEdgeId: String(edgeId),
    updatedAt: now,
  };
}

export function shouldPersistHazardRouteReviewDraft(
  previous: HazardRouteReviewRecord | null | undefined,
  next: HazardRouteReviewRecord,
) {
  if (!previous) {
    return Object.keys(next.segmentDrafts).length > 0;
  }
  return !areSegmentDraftMapsEqual(previous.segmentDrafts, next.segmentDrafts);
}

export function updateHazardRouteReviewSegmentDraft(
  review: HazardRouteReviewRecord,
  edgeId: string | number,
  draft: AdminRoadSegmentAttributesUpdateRequest,
  now: string,
): HazardRouteReviewRecord {
  const normalizedEdgeId = String(edgeId);
  return {
    ...review,
    selectedSegmentEdgeId: normalizedEdgeId,
    updatedAt: now,
    segmentDrafts: {
      ...review.segmentDrafts,
      [normalizedEdgeId]: draft,
    },
  };
}

function areSegmentDraftMapsEqual(
  a: Record<string, AdminRoadSegmentAttributesUpdateRequest>,
  b: Record<string, AdminRoadSegmentAttributesUpdateRequest>,
) {
  const aKeys = Object.keys(a).sort();
  const bKeys = Object.keys(b).sort();
  if (aKeys.length !== bKeys.length) {
    return false;
  }
  return aKeys.every((key, index) => (
    key === bKeys[index] && areSegmentDraftsEqual(a[key], b[key])
  ));
}

function areSegmentDraftsEqual(
  a: AdminRoadSegmentAttributesUpdateRequest | undefined,
  b: AdminRoadSegmentAttributesUpdateRequest | undefined,
) {
  return normalizeDraftField(a?.walkAccess) === normalizeDraftField(b?.walkAccess)
    && normalizeDraftField(a?.brailleBlockState) === normalizeDraftField(b?.brailleBlockState)
    && normalizeDraftField(a?.audioSignalState) === normalizeDraftField(b?.audioSignalState)
    && normalizeDraftField(a?.widthState) === normalizeDraftField(b?.widthState)
    && normalizeDraftField(a?.surfaceState) === normalizeDraftField(b?.surfaceState)
    && normalizeDraftField(a?.stairsState) === normalizeDraftField(b?.stairsState)
    && normalizeDraftField(a?.signalState) === normalizeDraftField(b?.signalState);
}

function normalizeDraftField(value: unknown) {
  return value ?? null;
}

export function completeHazardRouteReview(review: HazardRouteReviewRecord, now: string): HazardRouteReviewRecord {
  return {
    ...review,
    stage: "COMPLETED",
    updatedAt: now,
    completedAt: now,
  };
}

export function routeReviewCompletionMessage(status?: AdminRoutingApplyStatus | null) {
  switch (status) {
    case "PENDING":
      return "검수 완료, 경로 반영 필요 상태입니다.";
    case "APPLIED":
      return "검수 완료 즉시 반영 완료되었습니다.";
    case "APPLIED_WITH_WARNING":
      return "경로 반영은 수행됐지만 일부 경고가 있습니다. 운영 상태를 확인해 주세요.";
    case "FAILED":
      return "경로 반영에 실패했습니다. 저장된 변경은 유지되며 다시 시도할 수 있습니다.";
    case "SKIPPED":
      return "검수 완료, 경로 반영 대상이 없습니다.";
    default:
      return "검수 또는 저장이 완료되었습니다.";
  }
}

export function routeReviewCompletionClassName(status?: AdminRoutingApplyStatus | null) {
  switch (status) {
    case "FAILED":
      return "error-box";
    case "PENDING":
      return "warning-box";
    case "APPLIED_WITH_WARNING":
      return "warning-box";
    case "APPLIED":
      return "success-box";
    case "SKIPPED":
      return "info-box";
    default:
      return "info-box";
  }
}

export function deriveHazardDisplayStatus(
  baseStatus: HazardReportStatus,
  review?: HazardRouteReviewRecord | null,
): HazardDisplayStatus {
  if (review?.stage === "IN_PROGRESS") {
    return {
      key: "IN_PROGRESS",
      label: review.intent === "restore" ? "원상복구 진행중" : "진행중",
      tone: "blue",
    };
  }
  if (review?.stage === "COMPLETED" && review.intent === "restore") {
    return { key: "RESTORED", label: "원상복구 완료", tone: "purple" };
  }
  if (review?.stage === "COMPLETED" && review.intent === "approve") {
    return { key: "COMPLETED", label: "완료", tone: "green" };
  }
  if (baseStatus === "APPROVED") {
    return { key: "COMPLETED", label: "완료", tone: "green" };
  }
  if (baseStatus === "REJECTED") {
    return { key: "REJECTED", label: "반려", tone: "red" };
  }
  return { key: "PENDING", label: "대기", tone: "orange" };
}

export function deriveHazardDbSyncStatus(
  baseStatus: HazardReportStatus,
  review?: HazardRouteReviewRecord | null,
  routingApplyState?: AdminRoutingApplyStateResponse | null,
): HazardOperationStatus {
  if (review?.stage === "IN_PROGRESS") {
    return { label: review.intent === "restore" ? "복구 검수" : "검수중", tone: "blue" };
  }
  if (review?.stage === "COMPLETED" && review.intent === "restore") {
    return deriveCompletedReviewRoutingStatus(review, routingApplyState);
  }
  if (review?.stage === "COMPLETED" && review.intent === "approve") {
    return deriveCompletedReviewRoutingStatus(review, routingApplyState);
  }
  if (baseStatus === "APPROVED") {
    if (isRoutingApplyCompleted(routingApplyState)) {
      return {
        label: "반영완료",
        tone: routingApplyState?.routingApplyStatus === "APPLIED_WITH_WARNING" ? "orange" : "green",
      };
    }
    return { label: "-", tone: "gray" };
  }
  if (baseStatus === "REJECTED") {
    return { label: "-", tone: "gray" };
  }
  return { label: "-", tone: "gray" };
}

export function canStartHazardApprove(
  baseStatus: HazardReportStatus,
  review?: HazardRouteReviewRecord | null,
) {
  if (review?.stage === "IN_PROGRESS") {
    return false;
  }
  if (review?.stage === "COMPLETED" && review.intent === "approve") {
    return false;
  }
  return baseStatus === "PENDING" || baseStatus === "REJECTED";
}

export function canStartHazardRestore(
  baseStatus: HazardReportStatus,
  review?: HazardRouteReviewRecord | null,
) {
  if (review?.stage === "IN_PROGRESS") {
    return false;
  }
  if (review?.stage === "COMPLETED" && review.intent === "restore") {
    return false;
  }
  if (review?.stage === "COMPLETED" && review.intent === "approve") {
    return true;
  }
  return baseStatus === "APPROVED";
}

export function canRejectHazardReport(
  baseStatus: HazardReportStatus,
  _review?: HazardRouteReviewRecord | null,
) {
  return baseStatus === "PENDING";
}

export function canDeleteHazardReport(
  baseStatus: HazardReportStatus,
  review?: HazardRouteReviewRecord | null,
) {
  if (review?.stage === "IN_PROGRESS") {
    return false;
  }
  return baseStatus === "APPROVED" || baseStatus === "REJECTED";
}

export function resolveActiveHazardRouteReview(
  baseStatus: HazardReportStatus,
  review?: HazardRouteReviewRecord | null,
) {
  if (!review) {
    return null;
  }
  if (baseStatus === "REJECTED" && review.stage === "IN_PROGRESS") {
    return null;
  }
  return review;
}

export function isHazardRestorePending(review?: HazardRouteReviewRecord | null) {
  return review?.intent === "restore" && review.stage === "IN_PROGRESS";
}

export function isHazardReviewActive(review?: HazardRouteReviewRecord | null) {
  return review?.stage === "IN_PROGRESS";
}

export function isHazardRoutingApplyPending(
  review?: HazardRouteReviewRecord | null,
  routingApplyState?: AdminRoutingApplyStateResponse | null,
) {
  return review?.stage === "COMPLETED"
    && (
      review.routingApplyStatus === "PENDING"
      || review.routingApplyStatus === "FAILED"
      || isLegacyReviewPendingRoutingApply(review, routingApplyState)
    );
}

export function hazardRouteReviewIntentLabel(intent: HazardRouteReviewIntent) {
  return intent === "restore" ? "원상복구 검수" : "승인 검수";
}

function fromAdminHazardRouteReviewIntent(intent: AdminHazardRouteReviewIntent): HazardRouteReviewIntent {
  return intent === "RESTORE" ? "restore" : "approve";
}

function isAdminRoutingApplyStatus(value: unknown): value is AdminRoutingApplyStatus {
  return value === "PENDING"
    || value === "SKIPPED"
    || value === "APPLIED"
    || value === "APPLIED_WITH_WARNING"
    || value === "FAILED";
}

function isRoutingApplyCompleted(state?: AdminRoutingApplyStateResponse | null) {
  if (!state || state.dirty || state.applying) {
    return false;
  }
  return state.routingApplyStatus === "APPLIED" || state.routingApplyStatus === "APPLIED_WITH_WARNING";
}

function deriveCompletedReviewRoutingStatus(
  review: HazardRouteReviewRecord,
  routingApplyState?: AdminRoutingApplyStateResponse | null,
): HazardOperationStatus {
  switch (review.routingApplyStatus) {
    case "PENDING":
      return { label: "DB 대기", tone: "orange" };
    case "FAILED":
      return { label: "반영실패", tone: "red" };
    case "APPLIED":
      return { label: "반영완료", tone: "green" };
    case "APPLIED_WITH_WARNING":
      return { label: "반영경고", tone: "orange" };
    case "SKIPPED":
      return { label: "대상 없음", tone: "gray" };
    default:
      if (isLegacyReviewPendingRoutingApply(review, routingApplyState)) {
        return routingApplyState?.routingApplyStatus === "FAILED"
          ? { label: "반영실패", tone: "red" }
          : { label: "DB 대기", tone: "orange" };
      }
      if (isRoutingApplyCompleted(routingApplyState)) {
        return {
          label: "반영완료",
          tone: routingApplyState?.routingApplyStatus === "APPLIED_WITH_WARNING" ? "orange" : "green",
        };
      }
      return { label: "-", tone: "gray" };
  }
}

function isLegacyReviewPendingRoutingApply(
  review: HazardRouteReviewRecord,
  routingApplyState?: AdminRoutingApplyStateResponse | null,
) {
  if (review.routingApplyStatus != null || !routingApplyState?.dirty) {
    return false;
  }
  const completedAt = timestampMs(review.completedAt ?? review.updatedAt);
  if (completedAt == null) {
    return false;
  }
  const lastAppliedAt = timestampMs(routingApplyState.lastAppliedAt);
  return lastAppliedAt == null || completedAt > lastAppliedAt;
}

function timestampMs(value?: string | null) {
  if (!value) {
    return null;
  }
  const timestamp = new Date(value).getTime();
  return Number.isNaN(timestamp) ? null : timestamp;
}
