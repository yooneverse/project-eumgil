import { useMemo, useState } from "react";
import { AdminBottleneckKakaoMap } from "../map/AdminBottleneckKakaoMap";
import type { BottleneckMonitoringResponse } from "./bottleneckMonitoringContract";

export function BottleneckMonitoringPage({
  data,
  loading,
  error,
}: {
  data?: BottleneckMonitoringResponse;
  loading: boolean;
  error?: Error | null;
}) {
  if (!data) {
    return (
      <section className="bottleneck-monitoring-page admin-page-content">
        {loading && <p className="admin-card-inline-state">병목구간 통계를 불러오는 중입니다.</p>}
        {error && <p className="admin-card-inline-state error">{error.message}</p>}
      </section>
    );
  }

  const [typeFilter, setTypeFilter] = useState(data.table.filters.typeLabel);
  const [statusFilter, setStatusFilter] = useState(data.table.filters.statusLabel);
  const [sortFilter, setSortFilter] = useState(data.table.filters.sortLabel);
  const [impactSort, setImpactSort] = useState(data.impactTop.sortLabel);
  const donutBackground = useMemo(() => buildDonutGradient(data.distribution.items), [data.distribution.items]);

  return (
    <section className="bottleneck-monitoring-page admin-page-content">
      <div className="bottleneck-monitoring-metrics">
        {data.summaryCards.map((card) => (
          <KpiCard key={card.label} card={card} />
        ))}
      </div>

      <div className="bottleneck-monitoring-overview-grid map-leading map-emphasis">
        <article className="admin-dashboard-card bottleneck-monitoring-map-card">
          <header className="admin-card-header">
            <h3>병목구간 지도</h3>
          </header>
          <div className="bottleneck-monitoring-map-shell">
            <AdminBottleneckKakaoMap
              hotspots={data.map.hotspots}
              routeSegments={data.map.routeSegments}
            />
          </div>
        </article>

        <article className="admin-dashboard-card bottleneck-monitoring-distribution-card">
          <header className="admin-card-header">
            <h3>유형별 분포</h3>
          </header>
          <div className="bottleneck-monitoring-donut-layout">
            <div className="bottleneck-monitoring-donut" style={{ background: donutBackground }}>
              <div className="bottleneck-monitoring-donut-center">
                <span>총</span>
                <strong>{formatInteger(data.distribution.totalCount)}건</strong>
              </div>
            </div>
            <ul className="bottleneck-monitoring-donut-legend">
              {data.distribution.items.map((item) => (
                <li key={item.label}>
                  <i style={{ backgroundColor: item.color }} aria-hidden="true" />
                  <span>{item.label}</span>
                  <strong>{formatInteger(item.count)}건</strong>
                  <small>({formatPercent(item.share)})</small>
                </li>
              ))}
            </ul>
          </div>
        </article>

        <article className="admin-dashboard-card bottleneck-monitoring-trend-card compact-trend trend-graph-priority">
          <header className="admin-card-header">
            <div>
              <h3>병목구간 추이</h3>
            </div>
            <button type="button" className="bottleneck-monitoring-ghost-select">
              일별
            </button>
          </header>
          <TrendChart
            labels={data.trend.labels}
            series={data.trend.series}
            maxValue={data.trend.maxValue}
          />
        </article>
      </div>

      <div className="bottleneck-monitoring-bottom-grid">
        <article className="admin-dashboard-card bottleneck-monitoring-table-card">
          <header className="admin-card-header">
            <h3>병목구간 목록</h3>
          </header>

          <div className="bottleneck-monitoring-table-toolbar">
            <FilterSelect
              value={typeFilter}
              options={[data.table.filters.typeLabel, "좁은 보행로", "경사/단차", "횡단 주의"]}
              onChange={setTypeFilter}
            />
            <FilterSelect
              value={statusFilter}
              options={[data.table.filters.statusLabel, "심각", "주의", "보통"]}
              onChange={setStatusFilter}
            />
            <FilterSelect
              value={sortFilter}
              options={[data.table.filters.sortLabel, "영향 사용자순", "심각도순"]}
              onChange={setSortFilter}
            />
          </div>

          <div className="admin-table-scroll bottleneck-monitoring-table-scroll">
            <table className="admin-home-table bottleneck-monitoring-table">
              <thead>
                <tr>
                  <th>순위</th>
                  <th>위치</th>
                  <th>유형</th>
                  <th>영향 사용자</th>
                  <th>상태</th>
                  <th>최근 제보일</th>
                </tr>
              </thead>
              <tbody>
                {data.table.rows.slice(0, 5).map((row) => (
                  <tr key={`${row.rank}-${row.location}`}>
                    <td>{row.rank}</td>
                    <td>
                      <div className="bottleneck-monitoring-location-cell">
                        <strong>{row.location}</strong>
                        <small>{row.address}</small>
                      </div>
                    </td>
                    <td>
                      <span className={`bottleneck-monitoring-type-pill tone-${row.typeTone}`}>{row.typeLabel}</span>
                    </td>
                    <td className="bottleneck-monitoring-figure-cell">{row.affectedUsersLabel}</td>
                    <td>
                      <span className={`bottleneck-monitoring-status-pill tone-${row.statusTone}`}>{row.statusLabel}</span>
                    </td>
                    <td>{row.reportedAt}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <footer className="bottleneck-monitoring-table-footer">
            <div className="bottleneck-monitoring-pagination">
              <button type="button" aria-label="이전 페이지">‹</button>
              {data.table.pagination.pages.map((page) => (
                <button
                  key={page}
                  type="button"
                  className={page === data.table.pagination.currentPage ? "active" : ""}
                >
                  {page}
                </button>
              ))}
              <button type="button" aria-label="다음 페이지">›</button>
            </div>
            <button type="button" className="bottleneck-monitoring-ghost-select">
              {data.table.filters.pageSizeLabel}
            </button>
          </footer>
        </article>

        <article className="admin-dashboard-card bottleneck-monitoring-impact-card">
          <header className="admin-card-header">
            <div className="bottleneck-monitoring-inline-title">
              <h3>영향도 TOP 5</h3>
              <span aria-hidden="true">ⓘ</span>
            </div>
            <button type="button" className="bottleneck-monitoring-ghost-select" onClick={() => setImpactSort(data.impactTop.sortLabel)}>
              {impactSort}
            </button>
          </header>

          <ol className="bottleneck-monitoring-impact-list">
            {data.impactTop.items.map((item) => (
              <li key={`${item.rank}-${item.location}`}>
                <span className="bottleneck-monitoring-rank-circle">{item.rank}</span>
                <div className="bottleneck-monitoring-impact-copy">
                  <strong>{item.location}</strong>
                  <small>{item.affectedUsersLabel}</small>
                </div>
                <span className={`bottleneck-monitoring-status-pill tone-${item.statusTone}`}>{item.statusLabel}</span>
              </li>
            ))}
          </ol>

          <button type="button" className="bottleneck-monitoring-impact-more">
            전체 보기
            <span aria-hidden="true">›</span>
          </button>
        </article>
      </div>
    </section>
  );
}

function KpiCard({
  card,
}: {
  card: BottleneckMonitoringResponse["summaryCards"][number];
}) {
  const { numberPart, unitPart } = splitValueLabel(card.valueLabel);
  const toneClass = card.tone === "danger"
    ? "overview-metric-card-red"
    : card.tone === "success"
      ? "overview-metric-card-green"
      : "overview-metric-card-orange";
  const trendClass = card.tone === "success"
    ? "metric-trend bottleneck-monitoring-kpi-trend-success"
    : "metric-trend metric-trend-down";

  return (
    <article className={`overview-metric-card bottleneck-monitoring-kpi-card ${toneClass}`}>
      <div className="overview-metric-copy bottleneck-monitoring-kpi-copy">
        <span>{card.label}</span>
        <strong>
          {numberPart}
          {unitPart ? <small>{unitPart}</small> : null}
        </strong>
        <p className={trendClass}>
          <span>{card.deltaLabel}</span>
          <em>{card.comparisonLabel}</em>
        </p>
      </div>
      <div className="overview-metric-icon">
        <MetricIcon type={card.icon} />
      </div>
    </article>
  );
}

function FilterSelect({
  value,
  options,
  onChange,
}: {
  value: string;
  options: string[];
  onChange: (value: string) => void;
}) {
  return (
    <label className="bottleneck-monitoring-filter-select">
      <select value={value} onChange={(event) => onChange(event.target.value)}>
        {options.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </select>
    </label>
  );
}

function TrendChart({
  labels,
  series,
  maxValue,
}: {
  labels: string[];
  series: Array<{ label: string; color: string; values: number[] }>;
  maxValue: number;
}) {
  const left = 38;
  const right = 388;
  const top = 28;
  const bottom = 188;
  const yTicks = [0, 30, 60, 90, 120, 150];

  return (
    <div className="bottleneck-monitoring-chart-shell trend-graph-priority">
      <div className="bottleneck-monitoring-inline-legend">
        {series.map((item) => (
          <span key={item.label}>
            <i style={{ backgroundColor: item.color }} aria-hidden="true" />
            {item.label}
          </span>
        ))}
      </div>
      <svg viewBox="0 0 420 220" className="bottleneck-monitoring-svg" role="img" aria-label="병목구간 추이">
        {yTicks.map((tick) => (
          <g key={tick}>
            <line
              x1={left}
              y1={scaleY(tick, maxValue, top, bottom)}
              x2={right}
              y2={scaleY(tick, maxValue, top, bottom)}
              className="bottleneck-monitoring-grid-line"
            />
            <text x={left - 10} y={scaleY(tick, maxValue, top, bottom) + 4} className="bottleneck-monitoring-axis-label bottleneck-monitoring-axis-label-left">
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
                r="3.5"
                fill={item.color}
                stroke="#ffffff"
                strokeWidth="2"
              />
            ))}
          </g>
        ))}

        {labels.map((label, index) => (
          <text
            key={label}
            x={scaleX(index, labels.length, left, right)}
            y={bottom + 20}
            className="bottleneck-monitoring-axis-label"
          >
            {label}
          </text>
        ))}
      </svg>
    </div>
  );
}

function MetricIcon({ type }: { type: "alert" | "fire" | "users" | "check" }) {
  if (type === "alert") {
    return (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M12 3 2.8 19a1.2 1.2 0 0 0 1.04 1.8h16.32A1.2 1.2 0 0 0 21.2 19L12 3Z" fill="none" stroke="currentColor" strokeWidth="2" />
        <path d="M12 8v5.5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
        <circle cx="12" cy="17.2" r="1" fill="currentColor" />
      </svg>
    );
  }

  if (type === "fire") {
    return (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M12.6 3.5c1.2 2.8.3 4.7-1.2 6.4-1 1.1-1.8 2.2-1.8 3.8 0 2 1.6 3.6 3.6 3.6s3.8-1.8 3.8-4.2c0-2-1-3.6-2.4-5.4-.8-1-1.5-2.2-2-4.2Z" fill="none" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" />
        <path d="M9.8 13.4c-.8 1-1.3 2-1.3 3.2 0 2.1 1.6 3.9 3.7 3.9 2.4 0 4.3-1.8 4.3-4.4 0-1.1-.3-2-1-3" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
      </svg>
    );
  }

  if (type === "users") {
    return (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <circle cx="9" cy="9" r="3.2" fill="none" stroke="currentColor" strokeWidth="2" />
        <circle cx="16.8" cy="8.2" r="2.6" fill="none" stroke="currentColor" strokeWidth="2" />
        <path d="M4.4 18.2c.8-2.6 2.8-3.9 4.9-3.9 2.2 0 4.1 1.3 5 3.9" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
        <path d="M14.8 18.2c.4-1.9 1.8-3 3.5-3 1.5 0 2.8.8 3.3 2.3" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
      </svg>
    );
  }

  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="12" r="8.5" fill="none" stroke="currentColor" strokeWidth="2" />
      <path d="m8.5 12.3 2.3 2.3 4.8-5.1" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function buildDonutGradient(
  items: Array<{ color: string; share: number }>,
) {
  let cursor = 0;
  const stops = items.map((item) => {
    const from = cursor * 100;
    cursor += item.share;
    const to = cursor * 100;
    return `${item.color} ${from}% ${to}%`;
  });
  return `conic-gradient(${stops.join(", ")})`;
}

function splitValueLabel(value: string) {
  const match = value.match(/^([\d,.]+)(.*)$/);
  if (!match) {
    return {
      numberPart: value,
      unitPart: "",
    };
  }
  return {
    numberPart: match[1],
    unitPart: match[2].trim(),
  };
}

function formatInteger(value: number) {
  return new Intl.NumberFormat("ko-KR").format(value);
}

function formatPercent(value: number) {
  return `${(value * 100).toFixed(1)}%`;
}

function scaleX(index: number, length: number, left: number, right: number) {
  if (length <= 1) return left;
  const span = right - left;
  return left + (span / (length - 1)) * index;
}

function scaleY(value: number, maxValue: number, top: number, bottom: number) {
  const span = bottom - top;
  return bottom - (value / maxValue) * span;
}

function smoothPath(points: Array<{ x: number; y: number }>) {
  return points.reduce((path, point, index, source) => {
    if (index === 0) return `M ${point.x} ${point.y}`;
    const previous = source[index - 1];
    const cx = (previous.x + point.x) / 2;
    return `${path} C ${cx} ${previous.y}, ${cx} ${point.y}, ${point.x} ${point.y}`;
  }, "");
}
