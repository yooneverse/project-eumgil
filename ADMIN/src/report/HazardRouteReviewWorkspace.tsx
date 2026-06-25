import { useEffect, useMemo, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { fetchAdminRoadSegment } from "../api/adminApi";
import { SegmentMap } from "../map/SegmentMap";
import type {
  AccessibilityState,
  AdminRoadSegmentAttributesUpdateRequest,
  AdminRoutingApplyStateResponse,
  GeoPoint,
  SegmentFeature,
  SegmentPayload,
  WidthState,
} from "../types";
import {
  hazardRouteReviewIntentLabel,
  selectHazardRouteReviewSegment,
  updateHazardRouteReviewSegmentDraft,
  type HazardRouteReviewRecord,
} from "./hazardRouteReviewState";
import { formatHazardCoordinates } from "./hazardReportPresentation";

const accessibilityOptions: Array<{ value: AccessibilityState; label: string }> = [
  { value: "YES", label: "있음" },
  { value: "NO", label: "없음" },
  { value: "UNKNOWN", label: "미정" },
];

const widthOptions: Array<{ value: WidthState; label: string }> = [
  { value: "ADEQUATE_150", label: "150cm 이상" },
  { value: "ADEQUATE_120", label: "120cm 이상" },
  { value: "NARROW", label: "좁음" },
  { value: "UNKNOWN", label: "미정" },
];

interface HazardRouteReviewWorkspaceProps {
  review: HazardRouteReviewRecord;
  reportTypeLabel: string;
  reportPoint: GeoPoint;
  locationAddress: string | null;
  locationRegion: string;
  description: string | null | undefined;
  networkPayload?: SegmentPayload;
  networkLoading: boolean;
  networkError?: Error | null;
  areaScopeLabel: string | null;
  routingApplyState?: AdminRoutingApplyStateResponse | null;
  applyingRouting: boolean;
  refreshingRoutingState: boolean;
  savingReview: boolean;
  reviewSaveMessage: string | null;
  reviewSaveClassName?: string;
  onApplyRouting: () => void;
  onBack: () => void;
  onReviewChange: (review: HazardRouteReviewRecord) => void;
  onComplete: () => void;
  completing: boolean;
  accessToken: string;
  areaGu?: string | null;
  areaDong?: string | null;
}

export function HazardRouteReviewWorkspace({
  review,
  reportTypeLabel,
  reportPoint,
  locationAddress,
  locationRegion,
  description,
  networkPayload,
  networkLoading,
  networkError,
  areaScopeLabel,
  routingApplyState,
  applyingRouting,
  refreshingRoutingState,
  savingReview,
  reviewSaveMessage,
  reviewSaveClassName = "success-box",
  onApplyRouting,
  onBack,
  onReviewChange,
  onComplete,
  completing,
  accessToken,
  areaGu,
  areaDong,
}: HazardRouteReviewWorkspaceProps) {
  const roadviewContainerRef = useRef<HTMLDivElement | null>(null);
  const [selectedSegment, setSelectedSegment] = useState<SegmentFeature | null>(null);

  useEffect(() => {
    if (!networkPayload || !review.selectedSegmentEdgeId) {
      setSelectedSegment(null);
      return;
    }

    const matchedSegment = networkPayload.segments.features.find(
      (feature) => String(feature.properties.edgeId) === review.selectedSegmentEdgeId,
    ) ?? null;
    setSelectedSegment(matchedSegment);
  }, [networkPayload, review.selectedSegmentEdgeId]);

  const reviewedSegmentCount = Object.keys(review.segmentDrafts).length;
  const canComplete = reviewedSegmentCount > 0;
  const selectedSegmentEdgeId = selectedSegment ? String(selectedSegment.properties.edgeId) : review.selectedSegmentEdgeId;
  const selectedSegmentDetailQuery = useQuery({
    queryKey: ["admin-hazard-route-review-segment", selectedSegmentEdgeId, areaGu, areaDong, accessToken],
    queryFn: () => fetchAdminRoadSegment({
      edgeId: selectedSegmentEdgeId!,
      gu: areaGu!,
      dong: areaDong ?? undefined,
      accessToken,
    }),
    enabled: selectedSegmentEdgeId !== null && Boolean(accessToken && areaGu),
    retry: false,
  });
  const selectedSegmentForMap = selectedSegmentDetailQuery.data ?? selectedSegment;
  const selectedSegmentForAttributes = selectedSegmentDetailQuery.data
    ?? (selectedSegmentDetailQuery.isFetching ? null : selectedSegment);
  const selectedSegmentDraft = useMemo(() => {
    if (!selectedSegmentForAttributes) {
      return null;
    }

    const storedDraft = review.segmentDrafts[String(selectedSegmentForAttributes.properties.edgeId)] ?? {};
    return {
      walkAccess: normalizeAccessibility(storedDraft.walkAccess ?? selectedSegmentForAttributes.properties.walkAccess),
      stairsState: normalizeAccessibility(storedDraft.stairsState ?? selectedSegmentForAttributes.properties.stairsState),
      brailleBlockState: normalizeAccessibility(storedDraft.brailleBlockState ?? selectedSegmentForAttributes.properties.brailleBlockState),
      widthState: normalizeWidth(storedDraft.widthState ?? selectedSegmentForAttributes.properties.widthState),
    };
  }, [review.segmentDrafts, selectedSegmentForAttributes]);
  const segmentCardClassName = selectedSegmentForAttributes
    ? "hazard-detail-card hazard-review-segment-card selected"
    : "hazard-detail-card hazard-review-segment-card";

  function handleSelectSegment(feature: SegmentFeature) {
    setSelectedSegment(feature);
    onReviewChange(selectHazardRouteReviewSegment(review, feature.properties.edgeId, new Date().toISOString()));
  }

  function updateSelectedSegmentDraft(patch: Partial<AdminRoadSegmentAttributesUpdateRequest>) {
    if (!selectedSegmentForAttributes || !selectedSegmentDraft) return;
    onReviewChange(updateHazardRouteReviewSegmentDraft(
      review,
      selectedSegmentForAttributes.properties.edgeId,
      {
        ...selectedSegmentDraft,
        ...patch,
      },
      new Date().toISOString(),
    ));
  }

  return (
    <div className="hazard-review-shell">
      <div className="hazard-inline-banner hazard-review-banner">
        <span>세그먼트 속성 변경은 검수 초안으로 저장됩니다. 실제 DB 반영은 검수 완료 시 적용되고, 경로 반영은 별도로 실행합니다.</span>
      </div>

      <section className="hazard-detail-card hazard-review-map-card">
        <div className="hazard-review-map-card__header">
          <div>
            <strong>{hazardRouteReviewIntentLabel(review.intent)}</strong>
            <span>{areaScopeLabel ? `${areaScopeLabel} 좌표 주변 segment 검수` : "좌표 기준 검수 범위를 준비하는 중입니다."}</span>
          </div>
          <div className="hazard-review-map-card__meta">
            <span>신고 좌표</span>
            <strong>{formatHazardCoordinates(reportPoint)}</strong>
          </div>
        </div>

        <div className="hazard-review-map-frame">
          {selectedSegmentEdgeId && (
            <div className="hazard-review-selected-segment-pill" aria-live="polite">
              <span>선택한 세그먼트</span>
              <strong>edge {selectedSegmentEdgeId}</strong>
            </div>
          )}
          <SegmentMap
            payload={networkPayload}
            loading={networkLoading}
            error={networkError}
            draftEdits={[]}
            onDraftEdit={() => undefined}
            selectedSegment={selectedSegmentForMap}
            onSelectSegment={handleSelectSegment}
            roadviewContainerRef={roadviewContainerRef}
            onRoadviewChange={() => undefined}
            editable={false}
            toolbarMode="routeAttributeLegend"
            focusMarker={{
              point: reportPoint,
              label: "신고",
            }}
            preferredView={{
              point: reportPoint,
              level: 4,
            }}
          />
        </div>
      </section>

      <div className="hazard-review-grid">
        <section className="hazard-detail-card hazard-review-context-card">
          <h3>검수 기준</h3>
          <dl className="hazard-detail-list">
            <ReviewRow label="처리 흐름" value={review.intent === "restore" ? "정상화 검수" : "승인 검수"} />
            <ReviewRow label="신고 유형" value={reportTypeLabel} />
            <ReviewRow label="위치" value={locationAddress ?? "좌표 기준 위치 확인 중"} secondary={locationRegion || undefined} />
            <ReviewRow label="좌표" value={formatHazardCoordinates(reportPoint)} />
            <ReviewRow label="검수 현황" value={`검수한 세그먼트 ${reviewedSegmentCount}건`} secondary={savingReview ? "검수 초안 저장 중" : `마지막 변경 ${formatReviewStamp(review.updatedAt)}`} />
            <div className="hazard-detail-list__row hazard-detail-list__row--description">
              <dt>내용</dt>
              <dd>
                <div className="hazard-detail-list__description">
                  {description?.trim() || "작성된 제보 설명이 없습니다."}
                </div>
              </dd>
            </div>
          </dl>
        </section>

        <section className={segmentCardClassName}>
          <h3>세그먼트 검수</h3>
          <p className="hazard-review-helper">지도에서 세그먼트를 누르면 해당 edge의 핵심 통행 속성을 바로 검수할 수 있습니다.</p>
          {selectedSegmentEdgeId && selectedSegmentDetailQuery.isFetching && (
            <p className="muted">선택 segment의 DB 최신값을 확인하는 중입니다.</p>
          )}
          {selectedSegmentEdgeId && selectedSegmentDetailQuery.error && (
            <p className="error-box">선택 segment의 최신 DB 값을 불러오지 못해 지도 데이터 기준으로 표시합니다.</p>
          )}

          {selectedSegmentForAttributes && selectedSegmentDraft ? (
            <>
              <dl className="hazard-review-segment-meta">
                <ReviewMeta label="edge" value={String(selectedSegmentForAttributes.properties.edgeId)} />
                <ReviewMeta label="길이" value={formatDistanceValue(selectedSegmentForAttributes.properties.lengthMeter, "m")} />
                <ReviewMeta label="보도 폭" value={formatDistanceValue(selectedSegmentForAttributes.properties.widthMeter, "m")} />
                <ReviewMeta label="평균 경사" value={formatDistanceValue(selectedSegmentForAttributes.properties.avgSlopePercent, "%")} />
              </dl>

              <div className="hazard-review-field-grid">
                <ReviewSelect
                  label="통행 가능 여부"
                  value={selectedSegmentDraft.walkAccess}
                  options={accessibilityOptions}
                  onChange={(value) => updateSelectedSegmentDraft({ walkAccess: value as AccessibilityState })}
                />
                <ReviewSelect
                  label="계단"
                  value={selectedSegmentDraft.stairsState}
                  options={accessibilityOptions}
                  onChange={(value) => updateSelectedSegmentDraft({ stairsState: value as AccessibilityState })}
                />
                <ReviewSelect
                  label="점자블록"
                  value={selectedSegmentDraft.brailleBlockState}
                  options={accessibilityOptions}
                  onChange={(value) => updateSelectedSegmentDraft({ brailleBlockState: value as AccessibilityState })}
                />
                <ReviewSelect
                  label="보도 폭"
                  value={selectedSegmentDraft.widthState}
                  options={widthOptions}
                  onChange={(value) => updateSelectedSegmentDraft({ widthState: value as WidthState })}
                />
              </div>
            </>
          ) : (
            <div className="hazard-review-empty">
              <strong>검수할 세그먼트를 선택해 주세요.</strong>
              <span>신고 좌표 주변 도로를 클릭하면 통행 가능 여부와 계단, 점자블록, 보도 폭을 조정할 수 있습니다.</span>
            </div>
          )}
        </section>
      </div>

      <div className="hazard-review-actionbar">
        <div className="hazard-review-actionbar__summary">
          <strong>{hazardRouteReviewIntentLabel(review.intent)}</strong>
          <span>{canComplete ? `검수한 세그먼트 ${reviewedSegmentCount}건이 검수 완료 시 DB 반영 대상입니다. 초안 저장을 확인한 뒤 검수 완료를 누르고, 필요하면 경로 반영 버튼으로 적용해 주세요.` : "최소 1개 세그먼트를 검수해야 처리 완료를 진행할 수 있습니다."}</span>
          {savingReview && <span className="warning-box">검수 초안 저장 중</span>}
          {!savingReview && reviewSaveMessage && <span className={reviewSaveClassName}>{reviewSaveMessage}</span>}
          {refreshingRoutingState && <span className="warning-box">상태 새로고침 중</span>}
          <RoutingApplyStateSummary state={routingApplyState} />
        </div>
        <div className="hazard-review-actionbar__buttons">
          <button type="button" className="hazard-action-button secondary" onClick={onBack}>
            신고 상세로
          </button>
          <button
            type="button"
            className="hazard-action-button secondary"
            disabled={applyingRouting || refreshingRoutingState || !routingApplyState?.dirty}
            onClick={onApplyRouting}
          >
            {applyingRouting || routingApplyState?.applying ? "경로 반영 중" : "경로 반영"}
          </button>
          <button
            type="button"
            className="hazard-action-button approve"
            disabled={!canComplete || completing || savingReview}
            onClick={onComplete}
          >
            {savingReview ? "초안 저장 중" : completing ? "검수 완료 중" : "검수 완료"}
          </button>
        </div>
      </div>
    </div>
  );
}

function ReviewRow({
  label,
  value,
  secondary,
}: {
  label: string;
  value: string;
  secondary?: string;
}) {
  return (
    <div className="hazard-detail-list__row">
      <dt>{label}</dt>
      <dd>
        <div className="hazard-detail-list__value">
          <strong>{value}</strong>
          {secondary && <small>{secondary}</small>}
        </div>
      </dd>
    </div>
  );
}

function ReviewMeta({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function ReviewSelect({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: string;
  options: Array<{ value: string; label: string }>;
  onChange: (value: string) => void;
}) {
  return (
    <label className="hazard-review-select">
      <span>{label}</span>
      <select value={value} onChange={(event) => onChange(event.target.value)}>
        {options.map((option) => (
          <option key={option.value} value={option.value}>{option.label}</option>
        ))}
      </select>
    </label>
  );
}

function RoutingApplyStateSummary({ state }: { state?: AdminRoutingApplyStateResponse | null }) {
  if (!state) {
    return null;
  }
  return (
    <span className={routingApplyStateClassName(state)}>
      {routingApplyStateLabel(state)}
      {state.lastAppliedAt ? ` · 마지막 반영 ${formatReviewStamp(state.lastAppliedAt)}` : ""}
    </span>
  );
}

function normalizeAccessibility(value: unknown): AccessibilityState {
  return value === "YES" || value === "NO" ? value : "UNKNOWN";
}

function normalizeWidth(value: unknown): WidthState {
  return value === "ADEQUATE_150" || value === "ADEQUATE_120" || value === "NARROW" ? value : "UNKNOWN";
}

function formatDistanceValue(value: number | string | null | undefined, suffix: "m" | "%") {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return "-";
  return `${numeric.toFixed(suffix === "%" ? 1 : 2)}${suffix}`;
}

function formatReviewStamp(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString("ko-KR");
}

function routingApplyStateLabel(state: AdminRoutingApplyStateResponse) {
  if (state.applying) return "경로 반영 중";
  if (state.dirty && state.routingApplyStatus === "FAILED") return "경로 반영 실패";
  if (state.dirty) return "경로 반영 필요";
  if (state.routingApplyStatus === "FAILED") return "경로 반영 실패";
  if (state.routingApplyStatus === "SKIPPED") return "경로 반영 대상 없음";
  return "경로 반영 완료";
}

function routingApplyStateClassName(state: AdminRoutingApplyStateResponse) {
  if (state.applying) return "warning-box";
  if (state.dirty && state.routingApplyStatus === "FAILED") return "error-box";
  if (state.dirty) return "warning-box";
  if (state.routingApplyStatus === "FAILED") return "error-box";
  return "success-box";
}
