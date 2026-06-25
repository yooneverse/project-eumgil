import { describe, expect, it } from "vitest";
import source from "./HazardReportsPage.tsx?raw";

describe("HazardReportsPage route review copy", () => {
  it("does not expose legacy db sync queue messaging", () => {
    expect(source).not.toContain("전체 DB 반영 대기");
    expect(source).not.toContain("DB 반영 대기");
    expect(source).toContain("검수 완료 즉시");
    expect(source).toContain("즉시 반영 완료");
  });
});
