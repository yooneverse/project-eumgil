import { describe, expect, it } from "vitest";
import { facilityCategoryColor, facilityCategoryLabel } from "./facilityStyle";

describe("facility style", () => {
  it("maps known categories to stable colors and labels", () => {
    expect(facilityCategoryColor("PUBLIC_OFFICE")).toBe("#2563eb");
    expect(facilityCategoryLabel("PUBLIC_OFFICE")).toBe("공공");
    expect(facilityCategoryColor("HEALTHCARE")).toBe("#dc2626");
    expect(facilityCategoryLabel("HEALTHCARE")).toBe("의료");
  });

  it("falls back to ETC for unknown categories", () => {
    expect(facilityCategoryColor("UNKNOWN")).toBe("#64748b");
    expect(facilityCategoryLabel("UNKNOWN")).toBe("기타");
  });
});
