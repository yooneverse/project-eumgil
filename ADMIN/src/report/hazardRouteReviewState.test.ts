import { afterEach, describe, expect, it, vi } from "vitest";
import {
  canDeleteHazardReport,
  canRejectHazardReport,
  canStartHazardApprove,
  canStartHazardRestore,
  completeHazardRouteReview,
  deriveHazardDbSyncStatus,
  deriveHazardDisplayStatus,
  hazardRouteReviewIntentLabel,
  hydrateHazardRouteReviewRecord,
  isHazardRoutingApplyPending,
  isHazardRestorePending,
  loadStoredHazardRouteReview,
  resolveActiveHazardRouteReview,
  routeReviewCompletionClassName,
  routeReviewCompletionMessage,
  selectHazardRouteReviewSegment,
  shouldPersistHazardRouteReviewDraft,
  startHazardRouteReview,
  storeHazardRouteReview,
  updateHazardRouteReviewSegmentDraft,
} from "./hazardRouteReviewState";

describe("hazard route review workflow state", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("stores and restores in-progress drafts for resume", () => {
    const storage = new Map<string, string>();
    vi.stubGlobal("window", {
      localStorage: {
        getItem: (key: string) => storage.get(key) ?? null,
        setItem: (key: string, value: string) => storage.set(key, value),
        removeItem: (key: string) => storage.delete(key),
      },
    });

    const review = updateHazardRouteReviewSegmentDraft(startHazardRouteReview({
      reportId: 12,
      intent: "approve",
      reviewerUserId: "admin-1",
      now: "2026-05-18T02:00:00.000Z",
    }), "edge-7", { walkAccess: "NO", stairsState: "YES" }, "2026-05-18T02:01:00.000Z");

    storeHazardRouteReview(review);

    expect(loadStoredHazardRouteReview(12)).toMatchObject({
      reportId: 12,
      intent: "approve",
      stage: "IN_PROGRESS",
      selectedSegmentEdgeId: "edge-7",
      segmentDrafts: {
        "edge-7": {
          walkAccess: "NO",
          stairsState: "YES",
        },
      },
    });
  });

  it("derives list/detail status from local review progress before backend wiring is finished", () => {
    const review = startHazardRouteReview({
      reportId: 7,
      intent: "approve",
      reviewerUserId: "admin-2",
      now: "2026-05-18T03:00:00.000Z",
    });

    expect(deriveHazardDisplayStatus("PENDING", review)).toMatchObject({
      key: "IN_PROGRESS",
      tone: "blue",
    });

    expect(deriveHazardDisplayStatus("PENDING", completeHazardRouteReview(review, "2026-05-18T03:10:00.000Z"))).toMatchObject({
      key: "COMPLETED",
      tone: "green",
    });

    expect(deriveHazardDisplayStatus("APPROVED")).toMatchObject({
      key: "COMPLETED",
      label: "완료",
      tone: "green",
    });

    expect(deriveHazardDisplayStatus("APPROVED", completeHazardRouteReview(review, "2026-05-18T03:11:00.000Z"))).toMatchObject({
      key: "COMPLETED",
      label: "완료",
      tone: "green",
    });

    expect(deriveHazardDisplayStatus("APPROVED", startHazardRouteReview({
      reportId: 7,
      intent: "restore",
      reviewerUserId: "admin-2",
      now: "2026-05-18T03:11:30.000Z",
    }))).toMatchObject({
      key: "IN_PROGRESS",
      label: "원상복구 진행중",
      tone: "blue",
    });

    expect(deriveHazardDisplayStatus("APPROVED", completeHazardRouteReview({
      ...review,
      intent: "restore",
    }, "2026-05-18T03:12:00.000Z"))).toMatchObject({
      key: "RESTORED",
      tone: "purple",
    });
  });

  it("only allows restore review for already approved reports", () => {
    expect(canStartHazardRestore("PENDING")).toBe(false);
    expect(canStartHazardRestore("APPROVED")).toBe(true);
    expect(canStartHazardRestore("REJECTED")).toBe(false);
    expect(canStartHazardRestore("PENDING", completeHazardRouteReview(startHazardRouteReview({
      reportId: 3,
      intent: "approve",
      reviewerUserId: "admin-3",
      now: "2026-05-18T03:20:00.000Z",
    }), "2026-05-18T03:35:00.000Z"))).toBe(true);
    expect(hazardRouteReviewIntentLabel("restore")).toBeTruthy();
  });

  it("distinguishes restore pending from merely restorable approved reports", () => {
    expect(isHazardRestorePending()).toBe(false);

    const approveReview = completeHazardRouteReview(startHazardRouteReview({
      reportId: 8,
      intent: "approve",
      reviewerUserId: "admin-restore-check",
      now: "2026-05-18T04:00:00.000Z",
    }), "2026-05-18T04:10:00.000Z");

    const restoreReview = startHazardRouteReview({
      reportId: 9,
      intent: "restore",
      reviewerUserId: "admin-restore-check",
      now: "2026-05-18T04:20:00.000Z",
    });

    expect(isHazardRestorePending(approveReview)).toBe(false);
    expect(isHazardRestorePending(restoreReview)).toBe(true);
    expect(isHazardRestorePending(completeHazardRouteReview(restoreReview, "2026-05-18T04:30:00.000Z"))).toBe(false);
  });

  it("allows approve review for pending and rejected reports", () => {
    const review = startHazardRouteReview({
      reportId: 4,
      intent: "approve",
      reviewerUserId: "admin-4",
      now: "2026-05-18T03:40:00.000Z",
    });

    expect(canStartHazardApprove("PENDING")).toBe(true);
    expect(canStartHazardApprove("REJECTED")).toBe(true);
    expect(canStartHazardApprove("APPROVED")).toBe(false);
    expect(canStartHazardApprove("REJECTED", review)).toBe(false);
    expect(canStartHazardApprove("REJECTED", completeHazardRouteReview(review, "2026-05-18T03:55:00.000Z"))).toBe(false);
  });

  it("keeps reject available for pending reports while any review is in progress", () => {
    const approveReview = startHazardRouteReview({
      reportId: 14,
      intent: "approve",
      reviewerUserId: "admin-reject",
      now: "2026-05-18T05:00:00.000Z",
    });
    const staleRestoreReview = startHazardRouteReview({
      reportId: 14,
      intent: "restore",
      reviewerUserId: "admin-reject",
      now: "2026-05-18T05:01:00.000Z",
    });

    expect(canRejectHazardReport("PENDING")).toBe(true);
    expect(canRejectHazardReport("PENDING", approveReview)).toBe(true);
    expect(canRejectHazardReport("PENDING", staleRestoreReview)).toBe(true);
    expect(canRejectHazardReport("APPROVED", approveReview)).toBe(false);
    expect(canRejectHazardReport("REJECTED", approveReview)).toBe(false);
  });

  it("allows deleting processed reports but keeps active reviews protected", () => {
    const restoreReview = startHazardRouteReview({
      reportId: 16,
      intent: "restore",
      reviewerUserId: "admin-delete",
      now: "2026-05-18T05:20:00.000Z",
    });

    expect(canDeleteHazardReport("PENDING")).toBe(false);
    expect(canDeleteHazardReport("APPROVED")).toBe(true);
    expect(canDeleteHazardReport("REJECTED")).toBe(true);
    expect(canDeleteHazardReport("APPROVED", restoreReview)).toBe(false);
    expect(canDeleteHazardReport("APPROVED", completeHazardRouteReview(restoreReview, "2026-05-18T05:25:00.000Z"))).toBe(true);
  });

  it("drops in-progress approve review display after the report is rejected", () => {
    const review = startHazardRouteReview({
      reportId: 15,
      intent: "approve",
      reviewerUserId: "admin-reject",
      now: "2026-05-18T05:10:00.000Z",
    });

    expect(resolveActiveHazardRouteReview("PENDING", review)).toBe(review);
    expect(resolveActiveHazardRouteReview("REJECTED", review)).toBeNull();
  });

  it("hydrates latest server route review into local workflow state", () => {
    const review = hydrateHazardRouteReviewRecord({
      reviewId: 21,
      reportId: 12,
      intent: "RESTORE",
      stage: "IN_PROGRESS",
      reportStatus: "APPROVED",
      reviewerUserId: "admin-8",
      gu: "부산진구",
      dong: "부전동",
      selectedSegmentEdgeId: 41231,
      startedAt: "2026-05-18T10:00:00",
      updatedAt: "2026-05-18T10:05:00",
      completedAt: null,
      routingApplyStatus: "FAILED",
      routingApplyMessage: "reload failed",
      segmentDrafts: [
        {
          edgeId: 41231,
          walkAccess: "NO",
          brailleBlockState: "UNKNOWN",
          audioSignalState: "UNKNOWN",
          widthState: "NARROW",
          surfaceState: null,
          stairsState: "YES",
          signalState: "UNKNOWN",
        },
      ],
    });

    expect(review).toEqual({
      reportId: 12,
      intent: "restore",
      stage: "IN_PROGRESS",
      reviewerUserId: "admin-8",
      gu: "부산진구",
      dong: "부전동",
      startedAt: "2026-05-18T10:00:00",
      updatedAt: "2026-05-18T10:05:00",
      completedAt: null,
      routingApplyStatus: "FAILED",
      routingApplyMessage: "reload failed",
      selectedSegmentEdgeId: "41231",
      segmentDrafts: {
        "41231": {
          walkAccess: "NO",
          brailleBlockState: "UNKNOWN",
          audioSignalState: "UNKNOWN",
          widthState: "NARROW",
          surfaceState: null,
          stairsState: "YES",
          signalState: "UNKNOWN",
        },
      },
    });
  });

  it("formats route review completion messages from routing apply status", () => {
    expect(routeReviewCompletionMessage("PENDING")).toContain("경로 반영 필요");
    expect(routeReviewCompletionMessage("APPLIED_WITH_WARNING")).toContain("경고");
    expect(routeReviewCompletionMessage("FAILED")).toContain("실패");
    expect(routeReviewCompletionMessage("SKIPPED")).toContain("대상");
  });

  it("does not persist a route review draft when only the selected segment changes", () => {
    const review = startHazardRouteReview({
      reportId: 91,
      intent: "approve",
      reviewerUserId: "admin-select",
      now: "2026-05-21T03:20:00.000Z",
    });
    const selectedOnly = selectHazardRouteReviewSegment(review, 42001, "2026-05-21T03:21:00.000Z");

    expect(shouldPersistHazardRouteReviewDraft(review, selectedOnly)).toBe(false);

    const withDraft = updateHazardRouteReviewSegmentDraft(
      selectedOnly,
      42001,
      { walkAccess: "NO", stairsState: "UNKNOWN" },
      "2026-05-21T03:22:00.000Z",
    );

    expect(shouldPersistHazardRouteReviewDraft(selectedOnly, withDraft)).toBe(true);
  });

  it("marks approved reports as applied after the bulk routing apply succeeds", () => {
    const review = {
      ...completeHazardRouteReview(startHazardRouteReview({
        reportId: 58,
        intent: "approve",
        reviewerUserId: "admin-apply",
        now: "2026-05-20T08:30:00.000Z",
      }), "2026-05-20T08:36:00.000Z"),
      routingApplyStatus: "PENDING" as const,
    };

    expect(deriveHazardDbSyncStatus("APPROVED", review, {
      routingApplyStatus: "PENDING",
      message: "DB 저장이 완료되었습니다. 경로 반영이 필요합니다.",
      dirty: true,
      applying: false,
      lastAppliedAt: null,
    })).toEqual({ label: "DB 대기", tone: "orange" });
    expect(isHazardRoutingApplyPending(review)).toBe(true);

    expect(deriveHazardDbSyncStatus("APPROVED", review, {
      routingApplyStatus: "APPLIED",
      message: "reloaded",
      dirty: false,
      applying: false,
      lastAppliedAt: "2026-05-20T08:40:00",
    })).toEqual({ label: "DB 대기", tone: "orange" });

    const appliedReview = {
      ...review,
      routingApplyStatus: "APPLIED" as const,
    };

    expect(deriveHazardDbSyncStatus("APPROVED", appliedReview, {
      routingApplyStatus: "APPLIED",
      message: "reloaded",
      dirty: false,
      applying: false,
      lastAppliedAt: "2026-05-20T08:40:00",
    })).toEqual({ label: "반영완료", tone: "green" });
    expect(isHazardRoutingApplyPending(appliedReview)).toBe(false);

    expect(deriveHazardDbSyncStatus("APPROVED", undefined, {
      routingApplyStatus: "PENDING",
      message: "다른 제보 반영 대기",
      dirty: true,
      applying: false,
      lastAppliedAt: "2026-05-20T08:40:00",
    })).toEqual({ label: "-", tone: "gray" });

    const legacyPendingReview = {
      ...review,
      routingApplyStatus: null,
      completedAt: "2026-05-20T08:45:00",
      updatedAt: "2026-05-20T08:45:00",
    };

    expect(deriveHazardDbSyncStatus("APPROVED", legacyPendingReview, {
      routingApplyStatus: "PENDING",
      message: "기존 null 데이터 보정",
      dirty: true,
      applying: false,
      lastAppliedAt: "2026-05-20T08:40:00",
    })).toEqual({ label: "DB 대기", tone: "orange" });
    expect(isHazardRoutingApplyPending(legacyPendingReview, {
      routingApplyStatus: "PENDING",
      message: "기존 null 데이터 보정",
      dirty: true,
      applying: false,
      lastAppliedAt: "2026-05-20T08:40:00",
    })).toBe(true);

    const legacyAppliedReview = {
      ...review,
      routingApplyStatus: null,
      completedAt: "2026-05-20T08:35:00",
      updatedAt: "2026-05-20T08:35:00",
    };

    expect(deriveHazardDbSyncStatus("APPROVED", legacyAppliedReview, {
      routingApplyStatus: "PENDING",
      message: "다른 최신 제보 반영 대기",
      dirty: true,
      applying: false,
      lastAppliedAt: "2026-05-20T08:40:00",
    })).toEqual({ label: "-", tone: "gray" });
    expect(isHazardRoutingApplyPending(legacyAppliedReview, {
      routingApplyStatus: "PENDING",
      message: "다른 최신 제보 반영 대기",
      dirty: true,
      applying: false,
      lastAppliedAt: "2026-05-20T08:40:00",
    })).toBe(false);
  });

  it("maps route review completion status to visual severity classes", () => {
    expect(routeReviewCompletionClassName("FAILED")).toBe("error-box");
    expect(routeReviewCompletionClassName("APPLIED_WITH_WARNING")).toBe("warning-box");
    expect(routeReviewCompletionClassName("PENDING")).toBe("warning-box");
    expect(routeReviewCompletionClassName("SKIPPED")).toBe("info-box");
  });
});
