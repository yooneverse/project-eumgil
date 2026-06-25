import { describe, expect, it } from "vitest";
import { roadAttributeStrokeColor, roadAttributeStrokeStyle, roadAttributeStrokeWeight } from "./roadAttributeStyle";

describe("road attribute style", () => {
  it("colors lines by slope severity", () => {
    expect(roadAttributeStrokeColor("VERY_GENTLE")).toBe("#15803d");
    expect(roadAttributeStrokeColor("DANGEROUS")).toBe("#7f1d1d");
    expect(roadAttributeStrokeColor("")).toBe("#64748b");
  });

  it("weights lines by width level", () => {
    expect(roadAttributeStrokeWeight("VERY_NARROW")).toBe(2);
    expect(roadAttributeStrokeWeight("VERY_WIDE")).toBe(6);
    expect(roadAttributeStrokeWeight(undefined)).toBe(4);
  });

  it("uses dashed patterns for nonstandard surface values", () => {
    expect(roadAttributeStrokeStyle("PAVED")).toBe("solid");
    expect(roadAttributeStrokeStyle("UNPAVED")).toBe("shortdash");
    expect(roadAttributeStrokeStyle("UNKNOWN")).toBe("shortdot");
  });
});
