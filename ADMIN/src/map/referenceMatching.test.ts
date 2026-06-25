import { describe, expect, it } from "vitest";
import { pointLineDistanceM } from "./referenceMatching";

describe("reference matching", () => {
  it("returns near zero for a point on a line", () => {
    expect(pointLineDistanceM([129, 35], [[128.9999, 35], [129.0001, 35]])).toBeLessThan(0.1);
  });

  it("estimates point to line distance in meters", () => {
    expect(pointLineDistanceM([129, 35.0001], [[128.9999, 35], [129.0001, 35]])).toBeGreaterThan(10);
  });
});
