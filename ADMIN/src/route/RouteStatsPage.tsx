import { useMemo, useState } from "react";
import { AdminBottleneckKakaoMap } from "../map/AdminBottleneckKakaoMap";
import type { AdminRouteStatsResponse } from "../types";
import type { RouteStatsDistributionMetric, RouteStatsResponse } from "./routeStatsContract";

const integerFormatter = new Intl.NumberFormat("ko-KR");
const percentFormatter = new Intl.NumberFormat("ko-KR", {
  maximumFractionDigits: 1,
  style: "percent",
});
const decimalFormatter = new Intl.NumberFormat("ko-KR", {
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
});

type RouteStatsPageData = RouteStatsResponse | AdminRouteStatsResponse;

interface RouteStatsPageProps {
  data?: RouteStatsPageData;
  loading?: boolean;
  error?: Error | null;
  dataSourceMode?: string;
  updatedAt?: number;
}

export function RouteStatsPage({
  data,
  loading = false,
  error,
}: RouteStatsPageProps) {
  if (!data) {
    return (
      <section className="route-stats-page">
        {loading && <p className="admin-card-inline-state">경로/이동 통계를 불러오는 중입니다.</p>}
        {error && <p className="admin-card-inline-state error">{error.message}</p>}
      </section>
    );
  }

  const [filters, setFilters] = useState(data.filters.defaults);
  const [distanceMetric, setDistanceMetric] = useState<RouteStatsDistributionMetric>("COUNT");
  const [mapMetric, setMapMetric] = useState(data.map.selectedMetric);
  const [mapMode, setMapMode] = useState(data.map.selectedMode);
  const [showDistrictBoundary, setShowDistrictBoundary] = useState(data.map.showDistrictBoundary);
  const donutBackground = useMemo(() => buildDonutGradient(data.typeBreakdown), [data.typeBreakdown]);

  return (
    <section className="route-stats-page">
      <div className="route-stats-filter-bar">
        <div className="route-stats-chip-row" role="tablist" aria-label="이동 유형 필터">
          {data.filters.mobilityOptions.map((option) => (
            <button
              key={option.value}
              type="button"
              role="tab"
              aria-selected={filters.mobility === option.value}
              className={filters.mobility === option.value ? "route-stats-chip active" : "route-stats-chip"}
              onClick={() => setFilters((current) => ({ ...current, mobility: option.value }))}
            >
              {option.label}
            </button>
          ))}
        </div>

        <FilterSelect
          label="시간 단위"
          value={filters.timeGranularity}
          options={data.filters.timeGranularityOptions}
          onChange={(value) => setFilters((current) => ({ ...current, timeGranularity: value }))}
        />

        <button
          type="button"
          className="route-stats-reset"
          onClick={() => {
            setFilters(data.filters.defaults);
            setDistanceMetric("COUNT");
          }}
        >
          필터 초기화
        </button>
      </div>

      <div className="route-stats-top-layout">
        <article className="admin-dashboard-card route-stats-map-card">
          <header className="admin-card-header route-stats-map-header">
            <div className="route-stats-map-title-row">
              <h3>{data.map.title}</h3>
              <label className="route-stats-inline-metric">
                <span>표시 지표</span>
                <select value={mapMetric} onChange={(event) => setMapMetric(event.target.value)}>
                  {data.map.metricOptions.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>
            </div>
            <div className="route-stats-map-controls">
              <div className="route-stats-map-mode-toggle">
                {data.map.modeOptions.map((option) => (
                  <button
                    key={option.value}
                    type="button"
                    className={mapMode === option.value ? "active" : ""}
                    onClick={() => setMapMode(option.value)}
                  >
                    {option.label}
                  </button>
                ))}
              </div>
            </div>
          </header>

          <div className="route-stats-map-shell">
            <AdminBottleneckKakaoMap
              hotspots={data.map.hotspots}
              routeSegments={data.map.routeSegments}
            />
            <button
              type="button"
              className={
                showDistrictBoundary
                  ? "route-stats-boundary-toggle route-stats-boundary-toggle-overlay active"
                  : "route-stats-boundary-toggle route-stats-boundary-toggle-overlay"
              }
              aria-pressed={showDistrictBoundary}
              onClick={() => setShowDistrictBoundary((current) => !current)}
            >
              <span className="route-stats-boundary-toggle-mark" aria-hidden="true">
                {showDistrictBoundary ? "✓" : ""}
              </span>
              <span>행정동 경계</span>
            </button>
          </div>
        </article>

        <article className="admin-dashboard-card route-stats-routes-card">
          <header className="admin-card-header">
            <div>
              <h3>주요 이동 구간 TOP 7</h3>
              <span>{data.topRoutesDefinition}</span>
            </div>
          </header>
          <table className="route-stats-table">
            <thead>
              <tr>
                <th>순위</th>
                <th>구간</th>
                <th>이동 경로 수</th>
                <th>비율</th>
              </tr>
            </thead>
            <tbody>
              {data.topRoutes.slice(0, 7).map((route) => (
                <tr key={route.rank}>
                  <td>
                    <span className={`route-stats-rank-badge route-stats-rank-badge-${route.tone}`}>{route.rank}</span>
                  </td>
                  <td title={route.name}>{route.name}</td>
                  <td>{formatInteger(route.routeCount)}건</td>
                  <td>{formatPercent(route.share)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </article>

        <section className="admin-dashboard-card route-stats-side-summary route-stats-donut-card">
          <header className="admin-card-header">
            <h3>이동 유형 비율</h3>
          </header>

          <div className="route-stats-donut-layout">
            <div className="route-stats-donut" style={{ background: donutBackground }}>
              <div className="route-stats-donut-center">
                <strong>{formatInteger(data.summary.totalTrips)}건</strong>
                <span>{data.summary.metricLabel}</span>
              </div>
            </div>

            <ul className="route-stats-donut-legend">
              {data.typeBreakdown.map((item) => (
                <li key={item.label}>
                  <i style={{ backgroundColor: item.color }} aria-hidden="true" />
                  <div className="route-stats-donut-legend-copy">
                    <span>{item.label}</span>
                    <small>{formatPercent(item.share)} ({formatInteger(item.count)}건)</small>
                  </div>
                </li>
              ))}
            </ul>
          </div>
        </section>

      </div>

      <div className="route-stats-bottom route-stats-support-grid">
        <section className="admin-dashboard-card route-stats-side-summary route-stats-heat-grid-card route-stats-heat-grid-panel">
          <header className="admin-card-header">
            <h3>{data.hourlyHeatmap.title}</h3>
          </header>

          <div className="route-stats-heat-grid">
            <div className="route-stats-heat-grid-top">
              <span />
              {data.hourlyHeatmap.xLabels.map((label) => (
                <span key={label}>{label}</span>
              ))}
            </div>
            <div className="route-stats-heat-grid-body">
              {data.hourlyHeatmap.values.map((row, rowIndex) => (
                <div key={data.hourlyHeatmap.yLabels[rowIndex]} className="route-stats-heat-grid-row">
                  <span className="route-stats-heat-grid-y">{data.hourlyHeatmap.yLabels[rowIndex]}</span>
                  {row.map((value, columnIndex) => (
                    <i
                      key={`${rowIndex}-${columnIndex}`}
                      className="route-stats-heat-grid-cell"
                      style={{ backgroundColor: heatColor(value) }}
                      aria-hidden="true"
                    />
                  ))}
                </div>
              ))}
            </div>
            <div className="route-stats-heat-grid-scale">
              <span>적음</span>
              <i aria-hidden="true" />
              <span>많음</span>
            </div>
          </div>
        </section>

        <article className="admin-dashboard-card route-stats-line-card">
          <header className="admin-card-header">
            <h3>시간대별 평균 속도</h3>
            <span className="route-stats-unit">(km/h)</span>
          </header>
          <div className="route-stats-visual-body">
            <LineChart
              labels={data.speedTrend.labels}
              series={data.speedTrend.series}
              maxValue={14}
            />
          </div>
        </article>

        <article className="admin-dashboard-card route-stats-distribution-card">
          <header className="admin-card-header">
            <div>
              <h3>거리 구간별 분포</h3>
              <span>(%)</span>
            </div>
            <div className="route-stats-segmented-toggle">
              <button
                type="button"
                className={distanceMetric === "COUNT" ? "active" : ""}
                onClick={() => setDistanceMetric("COUNT")}
              >
                건수
              </button>
              <button
                type="button"
                className={distanceMetric === "SHARE" ? "active" : ""}
                onClick={() => setDistanceMetric("SHARE")}
              >
                비율
              </button>
            </div>
          </header>
          <div className="route-stats-visual-body">
            <DistributionChart
              buckets={data.distanceDistribution.buckets}
              series={data.distanceDistribution.series}
              metric={distanceMetric}
            />
          </div>
        </article>

        <article className="admin-dashboard-card route-stats-average-distance-card">
          <header className="admin-card-header">
            <div>
              <h3>평균 이동 거리</h3>
              <span>(km)</span>
            </div>
          </header>
          <div className="route-stats-visual-body route-stats-average-body">
            <AverageDistanceBars items={data.averageDistance} />
          </div>
        </article>
      </div>
    </section>
  );
}

function FilterSelect<T extends string>({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: T;
  options: Array<{ value: T; label: string }>;
  onChange: (value: T) => void;
}) {
  return (
    <label className="route-stats-filter-select">
      <span>{label}</span>
      <select value={value} onChange={(event) => onChange(event.target.value as T)}>
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </label>
  );
}

function LineChart({
  labels,
  series,
  maxValue,
}: {
  labels: string[];
  series: Array<{ label: string; color: string; values: number[] }>;
  maxValue: number;
}) {
  const left = 34;
  const right = 444;
  const top = 22;
  const bottom = 182;
  const yTicks = [0, 3, 6, 9, 12, maxValue];

  return (
    <div className="route-stats-svg-wrap">
      <div className="route-stats-inline-legend">
        {series.map((item) => (
          <span key={item.label}>
            <i style={{ backgroundColor: item.color }} aria-hidden="true" />
            {item.label}
          </span>
        ))}
      </div>
      <svg viewBox="0 0 472 214" className="route-stats-svg" role="img" aria-label="시간대별 평균 속도 추이">
        {yTicks.map((tick) => (
          <g key={tick}>
            <line x1={left} y1={scaleY(tick, maxValue, top, bottom)} x2={right} y2={scaleY(tick, maxValue, top, bottom)} className="route-stats-grid-line" />
            <text x={left - 8} y={scaleY(tick, maxValue, top, bottom) + 4} className="route-stats-axis-label route-stats-axis-label-left">
              {tick}
            </text>
          </g>
        ))}

        {series.map((item) => (
          <g key={item.label}>
            <path
              d={smoothPath(item.values.map((value, index) => ({
                x: scaleX(index, labels.length, left, right),
                y: scaleY(value, maxValue, top, bottom),
              })))}
              fill="none"
              stroke={item.color}
              strokeWidth="3"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
            {item.values.map((value, index) => (
              <circle
                key={`${item.label}-${labels[index]}`}
                cx={scaleX(index, labels.length, left, right)}
                cy={scaleY(value, maxValue, top, bottom)}
                r="4"
                fill={item.color}
                stroke="#fff"
                strokeWidth="2"
              />
            ))}
          </g>
        ))}

        {labels.map((label, index) => (
          <text key={label} x={scaleX(index, labels.length, left, right)} y="206" className="route-stats-axis-label">
            {label}
          </text>
        ))}
      </svg>
    </div>
  );
}

function DistributionChart({
  buckets,
  series,
  metric,
}: {
  buckets: string[];
  series: Array<{ label: string; color: string; count: number[]; share: number[] }>;
  metric: RouteStatsDistributionMetric;
}) {
  const left = 28;
  const right = 444;
  const top = 18;
  const bottom = 178;
  const values = series.flatMap((item) => metric === "COUNT" ? item.count : item.share.map((value) => value * 100));
  const maxValue = Math.max(1, ...values);
  const groupWidth = (right - left) / buckets.length;
  const barWidth = Math.min(20, groupWidth / 4.1);
  const yTicks = [0, maxValue * 0.25, maxValue * 0.5, maxValue * 0.75, maxValue];

  return (
    <div className="route-stats-svg-wrap">
      <svg viewBox="0 0 472 206" className="route-stats-svg" role="img" aria-label="거리 구간별 이동 분포">
        {yTicks.map((tick, index) => (
          <g key={index}>
            <line x1={left} y1={scaleY(tick, maxValue, top, bottom)} x2={right} y2={scaleY(tick, maxValue, top, bottom)} className="route-stats-grid-line" />
            <text x={left - 8} y={scaleY(tick, maxValue, top, bottom) + 4} className="route-stats-axis-label route-stats-axis-label-left">
              {metric === "COUNT" ? Math.round(tick) : Math.round(tick)}
            </text>
          </g>
        ))}

        {buckets.map((bucket, bucketIndex) => {
          const groupStart = left + groupWidth * bucketIndex + groupWidth * 0.16;
          return (
            <g key={bucket}>
              {series.map((item, seriesIndex) => {
                const source = metric === "COUNT" ? item.count : item.share.map((value) => value * 100);
                const value = source[bucketIndex];
                const x = groupStart + seriesIndex * (barWidth + 8);
                const y = scaleY(value, maxValue, top, bottom);
                const height = bottom - y;
                return (
                  <rect key={item.label} x={x} y={y} width={barWidth} height={height} rx="4" fill={item.color} opacity={0.95} />
                );
              })}
              <text x={groupStart + barWidth + 8} y="198" className="route-stats-axis-label">
                {bucket}
              </text>
            </g>
          );
        })}
      </svg>

      <div className="route-stats-inline-legend">
        {series.map((item) => (
          <span key={item.label}>
            <i style={{ backgroundColor: item.color }} aria-hidden="true" />
            {item.label}
          </span>
        ))}
      </div>
    </div>
  );
}

function AverageDistanceBars({
  items,
}: {
  items: Array<{ label: string; kilometer: number; color: string }>;
}) {
  const maxValue = Math.max(...items.map((item) => item.kilometer));

  return (
    <div className="route-stats-average-bars">
      {items.map((item) => (
        <div key={item.label} className="route-stats-average-bar">
          <strong>{decimalFormatter.format(item.kilometer)}</strong>
          <div className="route-stats-average-bar-track">
            <i
              style={{
                background: `linear-gradient(180deg, ${item.color}, color-mix(in srgb, ${item.color} 72%, white))`,
                height: `${Math.max(24, (item.kilometer / maxValue) * 168)}px`,
              }}
              aria-hidden="true"
            />
          </div>
          <span>{item.label}</span>
        </div>
      ))}
    </div>
  );
}

function buildDonutGradient(items: Array<{ color: string; share: number }>) {
  let start = 0;
  const slices = items.map((item) => {
    const end = start + item.share * 360;
    const slice = `${item.color} ${start.toFixed(2)}deg ${end.toFixed(2)}deg`;
    start = end;
    return slice;
  });
  return `conic-gradient(${slices.join(", ")})`;
}

function formatInteger(value: number) {
  return integerFormatter.format(Math.round(value));
}

function formatPercent(value: number) {
  return percentFormatter.format(value);
}

function scaleX(index: number, length: number, left: number, right: number) {
  if (length <= 1) return (left + right) / 2;
  return left + ((right - left) * index) / (length - 1);
}

function scaleY(value: number, maxValue: number, top: number, bottom: number) {
  return bottom - (Math.max(0, value) / Math.max(1, maxValue)) * (bottom - top);
}

function smoothPath(points: Array<{ x: number; y: number }>) {
  if (!points.length) return "";
  if (points.length === 1) return `M ${points[0].x} ${points[0].y}`;

  return points.reduce((path, point, index) => {
    if (index === 0) return `M ${point.x} ${point.y}`;
    const previous = points[index - 1];
    const controlDistance = (point.x - previous.x) * 0.46;
    return `${path} C ${previous.x + controlDistance} ${previous.y}, ${point.x - controlDistance} ${point.y}, ${point.x} ${point.y}`;
  }, "");
}

function heatColor(value: number) {
  const stops: Array<{ at: number; color: [number, number, number] }> = [
    { at: 0, color: [230, 239, 255] },
    { at: 0.22, color: [191, 219, 254] },
    { at: 0.45, color: [96, 165, 250] },
    { at: 0.66, color: [52, 211, 153] },
    { at: 0.82, color: [250, 204, 21] },
    { at: 1, color: [249, 115, 22] },
  ];
  const nextIndex = stops.findIndex((stop) => value <= stop.at);
  if (nextIndex <= 0) return colorToString(stops[0].color);

  const previous = stops[nextIndex - 1];
  const next = stops[nextIndex];
  const ratio = (value - previous.at) / (next.at - previous.at);
  return colorToString([
    Math.round(previous.color[0] + (next.color[0] - previous.color[0]) * ratio),
    Math.round(previous.color[1] + (next.color[1] - previous.color[1]) * ratio),
    Math.round(previous.color[2] + (next.color[2] - previous.color[2]) * ratio),
  ]);
}

function colorToString([red, green, blue]: [number, number, number]) {
  return `rgb(${red}, ${green}, ${blue})`;
}
