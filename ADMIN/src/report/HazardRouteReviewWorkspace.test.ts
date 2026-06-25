import { describe, expect, it } from "vitest";
import source from "./HazardRouteReviewWorkspace.tsx?raw";
import pageSource from "./HazardReportsPage.tsx?raw";

describe("HazardRouteReviewWorkspace copy", () => {
  it("removes immediate route apply copy from the completion action", () => {
    expect(source).toContain("검수 완료");
    expect(source).toContain("검수 완료를 누르고, 필요하면 경로 반영 버튼");
    expect(source).not.toContain("즉시 반영");
  });

  it("keeps the clicked segment visibly selected in the report review workspace", () => {
    expect(source).toContain("hazard-review-selected-segment-pill");
    expect(source).toContain("선택한 세그먼트");
    expect(source).toContain("hazard-review-segment-card selected");
  });

  it("shows draft save and routing refresh state around completion and apply actions", () => {
    expect(source).toContain("savingReview");
    expect(source).toContain("reviewSaveMessage");
    expect(source).toContain("검수 초안 저장 중");
    expect(pageSource).toContain("검수 초안 저장 완료");
    expect(pageSource).not.toContain("message: \"DB 저장 완료\"");
    expect(source).toContain("상태 새로고침 중");
    expect(source).toContain("검수 완료 중");
  });

  it("loads the clicked segment attributes from the single-segment DB endpoint", () => {
    expect(source).toContain("fetchAdminRoadSegment");
    expect(source).toContain('queryKey: ["admin-hazard-route-review-segment"');
    expect(source).toContain("selectedSegmentForMap = selectedSegmentDetailQuery.data ?? selectedSegment");
    expect(source).toContain("selectedSegmentDetailQuery.isFetching ? null : selectedSegment");
    expect(source).toContain("selectedSegmentForAttributes.properties.walkAccess");
    expect(pageSource).toContain("areaGu={routeReviewAreaScope?.gu}");
    expect(pageSource).toContain("areaDong={routeReviewAreaScope?.dong}");
  });
});
