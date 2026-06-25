import { describe, expect, it } from "vitest";
import appSource from "./App.tsx?raw";

describe("Admin dashboard navigation wiring", () => {
  it("routes dashboard more buttons to the matching admin workspaces", () => {
    expect(appSource).toContain('onOpenRouteStats={() => setPage("routeStats")}');
    expect(appSource).toContain('onOpenBottleneckMonitoring={() => setPage("bottleneckMonitoring")}');
    expect(appSource).toContain('onOpenHazards={() => setPage("hazards")}');
    expect(appSource).toContain('actionTarget="routeStats"');
    expect(appSource).toContain('actionTarget="bottleneckMonitoring"');
    expect(appSource).toContain('actionTarget="hazards"');
  });

  it("wires route statistics and bottleneck monitoring pages to their dedicated admin APIs", () => {
    expect(appSource).toContain("fetchAdminRouteStats");
    expect(appSource).toContain("fetchAdminBottleneckMonitoring");
    expect(appSource).toContain('queryKey: ["admin-route-stats", accessToken]');
    expect(appSource).toContain('queryKey: ["admin-bottleneck-monitoring", accessToken]');
    expect(appSource).toContain('queryFn: () => fetchAdminRouteStats(accessToken)');
    expect(appSource).toContain('queryFn: () => fetchAdminBottleneckMonitoring(accessToken)');
    expect(appSource).toContain('{page === "routeStats" && (');
    expect(appSource).toContain("routeStatsQuery.data");
    expect(appSource).toContain("bottleneckMonitoringQuery.data");
    expect(appSource).toContain("data={usesRealAdminApi ? routeStatsQuery.data : routeStatsMockResponse}");
    expect(appSource).toContain("data={usesRealAdminApi ? bottleneckMonitoringQuery.data : bottleneckMonitoringMockResponse}");
  });

  it("keeps the main road-network inspection map loading by selected dong scope", () => {
    expect(appSource).toContain("queryFn: () => fetchAdminRoadNetworkPayload({ gu: selectedGu, dong: selectedDong, accessToken })");
    expect(appSource).not.toContain("radiusMeter: HAZARD_ROUTE_REVIEW_RADIUS_METER");
  });

  it("binds dashboard period controls to summary and bottleneck queries", () => {
    expect(appSource).toContain('queryKey: ["admin-dashboard-summary", accessToken, normalizedDashboardRange.from, normalizedDashboardRange.to]');
    expect(appSource).toContain('queryKey: ["admin-dashboard-bottlenecks", accessToken, normalizedDashboardRange.from, normalizedDashboardRange.to]');
    expect(appSource).toContain("fetchAdminDashboardSummary({");
    expect(appSource).toContain("fetchAdminDashboardBottlenecks({");
    expect(appSource).toContain("topbar-date-range-panel");
    expect(appSource).toContain("최근 7일");
    expect(appSource).toContain("최근 30일");
  });

  it("turns bottleneck map controls into a real heatmap or segment toggle", () => {
    expect(appSource).toContain('const [viewMode, setViewMode] = useState<"heatmap" | "segment">("heatmap")');
    expect(appSource).toContain('aria-label="지도 보기 전환"');
    expect(appSource).toContain('onClick={() => setViewMode("segment")}');
    expect(appSource).toContain('presentationMode={viewMode}');
    expect(appSource).toContain("속도 기준 (m/s)");
  });

  it("renders the notices workspace as an implementation-ready state", () => {
    expect(appSource).toContain('{page === "notices" && <NoticeComingSoonPage />}');
    expect(appSource).toContain("공지사항 관리 기능은 준비중입니다.");
    expect(appSource).toContain("현재 공지 등록, 수정, 삭제 기능은 제공되지 않습니다.");
  });
  it("refreshes both network tabs after road-network edits complete", () => {
    expect(appSource).toContain('invalidateQueries({ queryKey: ["admin-road-network"] })');
    expect(appSource).toContain('invalidateQueries({ queryKey: ["admin-road-network-bridges"] })');
    expect(appSource).toContain('invalidateQueries({ queryKey: ["admin-hazard-route-review-network"] })');
  });
});
