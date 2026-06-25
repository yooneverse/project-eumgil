import { afterEach, describe, expect, it, vi } from "vitest";
import {
  completeAdminHazardRouteReview,
  deleteAdminHazardReport,
  fetchAdminDashboardBottlenecks,
  fetchAdminDashboardSummary,
  fetchAdminHazardReportDetail,
  fetchAdminRoadNetworkPayload,
  fetchAdminRoadSegment,
  startAdminHazardRouteReview,
  updateAdminHazardRouteReview,
} from "./adminApi";

describe("admin hazard route review API", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("fetches latest route review with hazard report detail", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        status: "OK",
        message: "ok",
        data: {
          reportId: 1,
          reporterUserId: "user-1",
          reportType: "SIDEWALK_MISSING",
          description: null,
          reportPoint: { lat: 35.1, lng: 129.1 },
          status: "PENDING",
          createdAt: "2026-05-18T10:00:00",
          imageUrls: [],
          latestRouteReview: {
            reviewId: 8,
            reportId: 1,
            intent: "APPROVE",
            stage: "IN_PROGRESS",
            reportStatus: "PENDING",
            reviewerUserId: "admin-1",
            gu: "부산진구",
            dong: "부전동",
            selectedSegmentEdgeId: 41231,
            startedAt: "2026-05-18T10:05:00",
            updatedAt: "2026-05-18T10:06:00",
            completedAt: null,
            segmentDrafts: [],
          },
        },
      }),
    });
    vi.stubGlobal("fetch", fetchMock);

    const response = await fetchAdminHazardReportDetail(1, "token");

    expect(response.latestRouteReview?.reviewId).toBe(8);
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("/admin/hazard-reports/1"),
      expect.objectContaining({
        credentials: "include",
        headers: expect.objectContaining({
          Authorization: "Bearer token",
        }),
      }),
    );
  });

  it("deletes an admin hazard report through the dedicated endpoint", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        status: "OK",
        message: "ok",
        data: { reportId: 42 },
      }),
    });
    vi.stubGlobal("fetch", fetchMock);

    const response = await deleteAdminHazardReport(42, "token");

    expect(response.reportId).toBe(42);
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("/admin/hazard-reports/42"),
      expect.objectContaining({
        method: "DELETE",
        credentials: "include",
        headers: expect.objectContaining({
          Authorization: "Bearer token",
        }),
      }),
    );
  });

  it("starts, updates, and completes route review through dedicated endpoints", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          status: "OK",
          message: "ok",
          data: {
            reviewId: 8,
            reportId: 1,
            intent: "APPROVE",
            stage: "IN_PROGRESS",
            reportStatus: "PENDING",
            reviewerUserId: "admin-1",
            gu: "부산진구",
            dong: "부전동",
            selectedSegmentEdgeId: null,
            startedAt: "2026-05-18T10:05:00",
            updatedAt: "2026-05-18T10:05:00",
            completedAt: null,
            segmentDrafts: [],
          },
        }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          status: "OK",
          message: "ok",
          data: {
            reviewId: 8,
            reportId: 1,
            intent: "APPROVE",
            stage: "IN_PROGRESS",
            reportStatus: "PENDING",
            reviewerUserId: "admin-1",
            gu: "부산진구",
            dong: "부전동",
            selectedSegmentEdgeId: 41231,
            startedAt: "2026-05-18T10:05:00",
            updatedAt: "2026-05-18T10:06:00",
            completedAt: null,
            segmentDrafts: [],
          },
        }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          status: "OK",
          message: "ok",
          data: {
            reviewId: 8,
            reportId: 1,
            intent: "APPROVE",
            stage: "COMPLETED",
            reportStatus: "APPROVED",
            reviewerUserId: "admin-1",
            gu: "부산진구",
            dong: "부전동",
            selectedSegmentEdgeId: 41231,
            startedAt: "2026-05-18T10:05:00",
            updatedAt: "2026-05-18T10:07:00",
            completedAt: "2026-05-18T10:07:00",
            segmentDrafts: [],
          },
        }),
      });
    vi.stubGlobal("fetch", fetchMock);

    await startAdminHazardRouteReview(
      1,
      { intent: "APPROVE" },
      "token",
    );
    await updateAdminHazardRouteReview(
      1,
      { selectedSegmentEdgeId: 41231, segmentDrafts: [] },
      "token",
    );
    const completed = await completeAdminHazardRouteReview(1, "token");

    expect(completed.stage).toBe("COMPLETED");
    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      expect.stringContaining("/admin/hazard-reports/1/route-review/start"),
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ intent: "APPROVE" }),
      }),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      expect.stringContaining("/admin/hazard-reports/1/route-review"),
      expect.objectContaining({ method: "PATCH" }),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      3,
      expect.stringContaining("/admin/hazard-reports/1/route-review/complete"),
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("passes selected dashboard period to summary and bottleneck APIs", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue({
        ok: true,
        json: async () => ({
          status: "OK",
          message: "ok",
          data: {},
        }),
      });
    vi.stubGlobal("fetch", fetchMock);

    await fetchAdminDashboardSummary({
      accessToken: "token",
      from: "2026-05-01",
      to: "2026-05-19",
    });
    await fetchAdminDashboardBottlenecks({
      accessToken: "token",
      from: "2026-05-01",
      to: "2026-05-19",
      limit: 12,
    });

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      expect.stringContaining("/admin/dashboard/summary?from=2026-05-01&to=2026-05-19"),
      expect.anything(),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      expect.stringContaining("/admin/dashboard/bottlenecks?limit=12&from=2026-05-01&to=2026-05-19"),
      expect.anything(),
    );
  });

  it("fetches a single road segment detail with area scope", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        status: "OK",
        message: "ok",
        data: {
          type: "Feature",
          geometry: { type: "LineString", coordinates: [] },
          properties: {
            edgeId: 15206,
            walkAccess: "YES",
          },
        },
      }),
    });
    vi.stubGlobal("fetch", fetchMock);

    const response = await fetchAdminRoadSegment({
      edgeId: 15206,
      gu: "강서구",
      dong: "명지동",
      accessToken: "token",
    });

    expect(response.properties.walkAccess).toBe("YES");
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("/admin/road-network/segments/15206?gu=%EA%B0%95%EC%84%9C%EA%B5%AC&dong=%EB%AA%85%EC%A7%80%EB%8F%99"),
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: "Bearer token",
        }),
      }),
    );
  });

  it("requests full road-network payload for the selected area without review clipping params", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        status: "OK",
        message: "ok",
        data: {
          summary: {
            segmentCount: 0,
            visibleSegmentCount: 0,
            roadNodeCount: 0,
            visibleRoadNodeCount: 0,
          },
          bbox: null,
          segments: { type: "FeatureCollection", features: [] },
          roadNodes: { type: "FeatureCollection", features: [] },
          areaBoundary: null,
        },
      }),
    });
    vi.stubGlobal("fetch", fetchMock);

    await fetchAdminRoadNetworkPayload({
      gu: "강서구",
      dong: "명지동",
      accessToken: "token",
    });

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("/admin/road-network/segments?gu=%EA%B0%95%EC%84%9C%EA%B5%AC&dong=%EB%AA%85%EC%A7%80%EB%8F%99"),
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: "Bearer token",
        }),
      }),
    );
    expect(fetchMock.mock.calls[0]?.[0]).not.toContain("limit=");
    expect(fetchMock.mock.calls[0]?.[0]).not.toContain("centerLat=");
    expect(fetchMock.mock.calls[0]?.[0]).not.toContain("centerLng=");
    expect(fetchMock.mock.calls[0]?.[0]).not.toContain("radiusMeter=");
  });

  it("can request a road-network payload clipped around a hazard report point", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        status: "OK",
        message: "ok",
        data: {
          summary: {
            segmentCount: 0,
            visibleSegmentCount: 0,
            roadNodeCount: 0,
            visibleRoadNodeCount: 0,
          },
          bbox: null,
          segments: { type: "FeatureCollection", features: [] },
          roadNodes: { type: "FeatureCollection", features: [] },
          areaBoundary: null,
        },
      }),
    });
    vi.stubGlobal("fetch", fetchMock);

    await fetchAdminRoadNetworkPayload({
      gu: "강서구",
      dong: "명지동",
      centerLat: 35.1,
      centerLng: 129.1,
      radiusMeter: 300,
      accessToken: "token",
      limit: 500,
    });

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining(
        "/admin/road-network/segments?gu=%EA%B0%95%EC%84%9C%EA%B5%AC&dong=%EB%AA%85%EC%A7%80%EB%8F%99&limit=500&centerLat=35.1&centerLng=129.1&radiusMeter=300",
      ),
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: "Bearer token",
        }),
      }),
    );
  });
});
