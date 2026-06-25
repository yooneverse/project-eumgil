import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";

describe("BottleneckMonitoringPage", () => {
  it("renders the approved bottleneck operations dashboard layout", async () => {
    const pageModule = await import("./BottleneckMonitoringPage").catch(() => null);
    expect(pageModule).not.toBeNull();
    if (!pageModule) return;

    const contractModule = await import("./bottleneckMonitoringContract").catch(() => null);
    expect(contractModule).not.toBeNull();
    if (!contractModule) return;

    const html = renderToStaticMarkup(
      <pageModule.BottleneckMonitoringPage
        data={contractModule.bottleneckMonitoringMockResponse}
        loading={false}
      />,
    );

    expect(html).toContain("bottleneck-monitoring-page admin-page-content");
    expect(html).not.toContain("bottleneck-monitoring-page page-inset");
    expect(html).not.toContain("bottleneck-monitoring-subheader");
    expect(html).toContain("bottleneck-monitoring-metrics");
    expect(html).toContain("bottleneck-monitoring-overview-grid");
    expect(html).toContain("bottleneck-monitoring-bottom-grid");
    expect(html).toContain("bottleneck-monitoring-table-card");
    expect(html).toContain("bottleneck-monitoring-impact-card");
    expect(html).not.toContain("<h2>병목구간 통계</h2>");
    expect(html).not.toContain("이동 약자의 통행이 불편한 병목구간 현황을 확인할 수 있습니다.");
    expect(html).not.toContain("2024.05.01 ~ 2024.05.31");
    expect(html).not.toContain("CSV 다운로드");
    expect(html).toContain("overview-metric-card");
    expect(html).toContain("overview-metric-copy");
    expect(html).toContain("overview-metric-icon");
    expect(html).toContain("bottleneck-monitoring-kpi-card");
    expect(html).toContain("bottleneck-monitoring-overview-grid map-leading map-emphasis");
    expect(html).toContain("bottleneck-monitoring-trend-card compact-trend");
    expect(html).toContain("bottleneck-monitoring-trend-card compact-trend trend-graph-priority");
    expect(html).toContain("bottleneck-monitoring-chart-shell trend-graph-priority");
    expect(html).toContain("병목구간 총수");
    expect(html).toContain("실제 병목구간");
    expect(html).toContain("영향을 받은 사용자");
    expect(html).toContain("해결 완료");
    expect(html).toContain("병목구간 추이");
    expect(html).toContain("유형별 분포");
    expect(html).toContain("병목구간 지도");
    expect(html).toContain("병목구간 목록");
    expect(html).toContain("영향도 TOP 5");
    expect(html).toContain("서면역 7번 출구 주변");
    expect(html).toContain("해운대 해변로 입구");
    expect(html).toContain("전체 보기");
  });

  it("shows only loading state when real bottleneck monitoring data has not arrived yet", async () => {
    const pageModule = await import("./BottleneckMonitoringPage").catch(() => null);
    expect(pageModule).not.toBeNull();
    if (!pageModule) return;

    const html = renderToStaticMarkup(
      <pageModule.BottleneckMonitoringPage
        loading
      />,
    );

    expect(html).toContain("병목구간 통계를 불러오는 중입니다.");
    expect(html).not.toContain("병목구간 총수");
    expect(html).not.toContain("영향도 TOP 5");
  });

  it("does not render duplicate loading state when stale bottleneck data already exists", async () => {
    const pageModule = await import("./BottleneckMonitoringPage").catch(() => null);
    expect(pageModule).not.toBeNull();
    if (!pageModule) return;

    const contractModule = await import("./bottleneckMonitoringContract").catch(() => null);
    expect(contractModule).not.toBeNull();
    if (!contractModule) return;

    const html = renderToStaticMarkup(
      <pageModule.BottleneckMonitoringPage
        data={contractModule.bottleneckMonitoringMockResponse}
        loading
      />,
    );

    expect(html).not.toContain("병목구간 통계를 불러오는 중입니다.");
  });
});
