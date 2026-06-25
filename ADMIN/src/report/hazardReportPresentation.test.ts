import { describe, expect, it } from "vitest";
import {
  createKakaoMapLink,
  createKakaoRoadviewLink,
  formatHazardCoordinates,
  formatHazardTrackingId,
  formatHazardRegionLabel,
  pickHazardPrimaryImage,
  resolveHazardDisplayAddress,
} from "./hazardReportPresentation";

describe("hazard report presentation helpers", () => {
  const point = { lat: 35.0963981, lng: 128.8538764 };

  it("formats coordinates with a stable six-decimal precision", () => {
    expect(formatHazardCoordinates(point)).toBe("35.096398, 128.853876");
  });

  it("prefers displayAddress and falls back to roadAddress or jibun address", () => {
    expect(resolveHazardDisplayAddress({
      displayAddress: "부산진구 중앙대로 672",
      roadAddress: "부산진구 중앙대로 672",
      address: "부산진구 부전동 123-4",
      region1DepthName: "부산광역시",
      region2DepthName: "부산진구",
      region3DepthName: "부전동",
    })).toBe("부산진구 중앙대로 672");

    expect(resolveHazardDisplayAddress({
      displayAddress: null,
      roadAddress: null,
      address: "부산진구 부전동 123-4",
      region1DepthName: "부산광역시",
      region2DepthName: "부산진구",
      region3DepthName: "부전동",
    })).toBe("부산진구 부전동 123-4");
  });

  it("deduplicates region labels while preserving order", () => {
    expect(formatHazardRegionLabel({
      displayAddress: null,
      roadAddress: null,
      address: null,
      region1DepthName: "부산광역시",
      region2DepthName: "부산진구",
      region3DepthName: "부산진구",
    })).toBe("부산광역시 부산진구");
  });

  it("clamps the selected image index to the available preview range", () => {
    expect(pickHazardPrimaryImage(["one", "two"], 4)).toBe("two");
    expect(pickHazardPrimaryImage(["one", "two"], -3)).toBe("one");
  });

  it("builds Kakao external links from the same formatted coordinate source", () => {
    expect(createKakaoMapLink(point, "경사로 신고 위치")).toBe(
      "https://map.kakao.com/link/map/%EA%B2%BD%EC%82%AC%EB%A1%9C%20%EC%8B%A0%EA%B3%A0%20%EC%9C%84%EC%B9%98,35.096398,128.853876",
    );
    expect(createKakaoRoadviewLink(point)).toBe(
      "https://map.kakao.com/link/roadview/35.096398,128.853876",
    );
  });

  it("formats report ids into a dashboard-friendly tracking number", () => {
    expect(formatHazardTrackingId("2026-05-18T09:30:00", 1)).toBe("#2026-0518-0001");
    expect(formatHazardTrackingId("invalid", 27)).toBe("#report-0027");
  });
});
