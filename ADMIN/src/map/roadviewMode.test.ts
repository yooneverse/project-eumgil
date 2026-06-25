import { describe, expect, it } from "vitest";
import { roadviewUnavailableMessage, shouldOpenRoadviewForMode } from "./roadviewMode";

describe("roadviewUnavailableMessage", () => {
  it("returns no message when roadview client and viewer are available", () => {
    expect(roadviewUnavailableMessage(true, true)).toBeNull();
  });

  it("returns the existing editor unavailable message when roadview cannot open", () => {
    expect(roadviewUnavailableMessage(false, true)).toBe("Kakao Roadview is unavailable.");
  });
});

describe("shouldOpenRoadviewForMode", () => {
  it("opens roadview only in roadview mode", () => {
    expect(shouldOpenRoadviewForMode("roadview")).toBe(true);
    expect(shouldOpenRoadviewForMode("select")).toBe(false);
    expect(shouldOpenRoadviewForMode("delete")).toBe(false);
  });
});
