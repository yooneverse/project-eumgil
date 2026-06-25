import { describe, expect, it } from "vitest";
import type { RoadAttributeFeature } from "../types";
import { shouldShowRoadAttributeReference } from "./networkReferenceLayer";

function feature(slopeLevel: string, riskLevel: string): RoadAttributeFeature {
  return {
    type: "Feature",
    geometry: { type: "LineString", coordinates: [[129, 35], [129.1, 35.1]] },
    properties: {
      handoffEdgeId: "edge",
      sourceId: "source",
      districtGu: "강서구",
      slopeLevel,
      riskLevel,
    },
  };
}

describe("network reference layer", () => {
  it("shows low-risk road attributes on the walking network map", () => {
    expect(shouldShowRoadAttributeReference(feature("VERY_GENTLE", "LOW"))).toBe(true);
  });

  it("shows road attributes that need visual review too", () => {
    expect(shouldShowRoadAttributeReference(feature("STEEP", "MEDIUM"))).toBe(true);
    expect(shouldShowRoadAttributeReference(feature("VERY_GENTLE", "HIGH"))).toBe(true);
  });
});
