import { describe, expect, it } from "vitest";
import source from "./SegmentMap.tsx?raw";

describe("SegmentMap route review guardrail", () => {
  it("caps forced detailed rendering when too many segments are loaded", () => {
    expect(source).toContain("FORCED_DETAIL_SEGMENT_MAX_COUNT");
    expect(source).toContain("forceDetailedSegmentsBlocked");
  });

  it("renders every scoped detailed segment without a hard viewport cap", () => {
    expect(source).not.toContain("DETAIL_SEGMENT_RENDER_MAX_COUNT");
    expect(source).toContain("detailSegmentRenderScope");
    expect(source).toContain("features: orderedFeatures");
    expect(source).toContain("capped: false");
  });

  it("schedules detailed overlay creation in batches to protect the browser main thread", () => {
    expect(source).toContain("DETAIL_SEGMENT_RENDER_BATCH_SIZE");
    expect(source).toContain("scheduleOverlayRenderBatches");
    expect(source).toContain("requestAnimationFrame");
  });

  it("uses a high-contrast overlay for the selected segment", () => {
    expect(source).toContain('strokeColor: "#f97316"');
    expect(source).toContain("zIndex: 80");
  });
});
