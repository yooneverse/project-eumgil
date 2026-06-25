import { describe, expect, it } from "vitest";
import source from "./HazardReportsPage.tsx?raw";

describe("HazardReportsPage manual routing apply wiring", () => {
  it("connects the bulk apply button and refreshes apply-state after review completion", () => {
    expect(source).toContain("onClick={() => applyRoutingMutation.mutate()}");
    expect(source).toContain('invalidateQueries({ queryKey: ["admin-routing-apply-state"] })');
    expect(source).toContain('invalidateQueries({ queryKey: ["admin-hazard-route-review-network"] })');
    expect(source).toContain('meta: "(경로 반영 대기)"');
    expect(source).not.toContain("(諛깆뿏???곕룞 ?덉젙)");
  });

  it("keeps hazard route-review segment loading within the report radius", () => {
    expect(source).toContain("const HAZARD_ROUTE_REVIEW_RADIUS_METER = 300");
    expect(source).toContain("const HAZARD_ROUTE_REVIEW_SEGMENT_LIMIT = 500");
    expect(source).toContain("centerLat: reportPoint?.lat");
    expect(source).toContain("centerLng: reportPoint?.lng");
    expect(source).toContain('&& detailPaneMode === "review"');
    expect(source).toContain("radiusMeter: HAZARD_ROUTE_REVIEW_RADIUS_METER");
  });

  it("keeps the reject action available during approve review progress", () => {
    expect(source).toContain("canRejectHazardReport(detail.status, activeReviewDraft)");
    expect(source).toContain("clearRouteReviewDraft(response.reportId)");
    expect(source).not.toContain('disabled={!canReject || activeReviewDraft?.stage === "IN_PROGRESS"}');
  });

  it("keeps approved reports in the approved tab instead of a separate restore-pending queue", () => {
    expect(source).toContain('type HazardFilterKey = "" | HazardReportStatus;');
    expect(source).not.toContain('"RESTORE_PENDING" as HazardFilterKey');
    expect(source).not.toContain('status === "RESTORE_PENDING"');
    expect(source).not.toContain("restorePendingCount");
    expect(source).not.toContain("countRestorableReports");
    expect(source).not.toContain("countCompletedRestoreReviews");
    expect(source).toContain("const hasDbSyncQueue = preview");
    expect(source).toContain("isHazardRoutingApplyPending(filteredReportReviewDrafts.get(report.reportId) ?? null, routingApplyState)");
    expect(source).toContain("routeReviewDrafts[report.reportId] ?? hydrateHazardRouteReviewRecord(report.latestRouteReview)");
  });

  it("uses the rejected action slot as a delete action for processed reports", () => {
    expect(source).toContain("deleteAdminHazardReport(reportId, accessToken)");
    expect(source).toContain("canDeleteHazardReport(detail.status, activeReviewDraft)");
    expect(source).toContain('const showsDeleteAction = detail ? canDeleteHazardReport(detail.status, null) : false;');
    expect(source).toContain('confirm("처리된 제보를 삭제할까요?');
    expect(source).toContain("{showsDeleteAction ? \"삭제\" : \"반려\"}");
    expect(source).toContain('name={showsDeleteAction ? "trash" : "close"}');
  });
});
