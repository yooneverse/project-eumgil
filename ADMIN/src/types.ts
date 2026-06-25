export type UserRole = "USER" | "ADMIN";
export type AdminRole = "ADMIN";

export type SocialProvider = "KAKAO" | "NAVER" | "GOOGLE";

export interface AuthTestConfig {
  kakaoJavaScriptKey: string;
  naverClientId: string;
  googleClientId: string;
}

export interface SocialLoginResponse {
  signupRequired: boolean;
  signupToken: string | null;
  accessToken: string | null;
  refreshToken: string | null;
  userId: string | null;
  selectedPrimaryUserType: string | null;
  selectedMobilitySubtype: string | null;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
}

export type WorkStatus = "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED" | "HOLD";
export type AssignmentType = "ROAD_NETWORK" | "FACILITY";

export type AdminPage =
  | "home"
  | "network"
  | "routeTuning"
  | "routeStats"
  | "bottleneckMonitoring"
  | "facilities"
  | "hazards"
  | "notices"
  | "users"
  | "logs";

export type EditableSegmentType = "SIDE_LINE" | "CROSS_WALK";
export type SegmentFeatureType = "CROSSWALK" | "AUDIO_SIGNAL" | "BRAILLE_BLOCK" | "STAIRS";

export type PlaceCategory =
  | "FOOD_CAFE"
  | "TOURIST_SPOT"
  | "ACCOMMODATION"
  | "HEALTHCARE"
  | "WELFARE"
  | "PUBLIC_OFFICE"
  | "ETC";

export type AccessibilityFeatureType =
  | "accessibleEntrance"
  | "elevator"
  | "accessibleToilet"
  | "accessibleParking"
  | "chargingStation"
  | "accessibleRoom"
  | "guidanceFacility";

export interface Assignment {
  assignmentId: number | null;
  gu: string;
  dong: string;
  assignmentType: AssignmentType;
  assigneeUserId: string | null;
  assigneeLabel: string | null;
  status: WorkStatus;
  updatedAt: string | null;
}

export interface AreaOption {
  gu: string;
  dong: string;
}

export interface SegmentFeature {
  type: "Feature";
  bbox?: [number, number, number, number];
  geometry: {
    type: "LineString";
    coordinates: Array<[number, number]>;
  };
  properties: {
    edgeId: number | string;
    fromNodeId?: number | string;
    toNodeId?: number | string;
    segmentType?: EditableSegmentType | "TRANSITION_CONNECTOR" | string;
    lengthMeter?: number | string;
    avgSlopePercent?: number | string | null;
    widthMeter?: number | string | null;
    walkAccess?: string | null;
    brailleBlockState?: string | null;
    audioSignalState?: string | null;
    widthState?: string | null;
    surfaceState?: string | null;
    stairsState?: string | null;
    signalState?: string | null;
    featureTypes?: SegmentFeatureType[];
  };
}

export interface RoadNodeFeature {
  type: "Feature";
  geometry: {
    type: "Point";
    coordinates: [number, number];
  };
  properties: {
    vertexId: number | string;
    sourceNodeKey?: string | null;
  };
}

export interface AreaBoundaryFeature {
  type: "Feature";
  geometry: {
    type: "LineString" | "MultiLineString" | "Polygon" | "MultiPolygon";
    coordinates: unknown;
  };
  properties: {
    gu: string;
    dong: string;
  };
}

export interface SegmentPayload {
  summary?: {
    segmentCount?: number;
    nodeCount?: number;
    visibleSegmentCount?: number;
    bridgeCandidateCount?: number | null;
    visibleBridgeCandidateCount?: number;
  };
  csv?: {
    segmentCsv?: string;
    nodeCsv?: string;
  };
  bbox?: [number, number, number, number] | null;
  segments: {
    type: "FeatureCollection";
    features: SegmentFeature[];
  };
  roadNodes?: {
    type: "FeatureCollection";
    features: RoadNodeFeature[];
  };
  areaBoundary?: AreaBoundaryFeature | null;
  bridges?: {
    type: "FeatureCollection";
    features: BridgeFeature[];
  };
}

export interface BridgeFeature {
  type: "Feature";
  bbox?: [number, number, number, number];
  geometry: {
    type: "LineString";
    coordinates: Array<[number, number]>;
  };
  properties: {
    candidateId: string;
    type: "PROPOSED_BRIDGE";
    priority?: "AUTO" | "REVIEW" | string;
    fromNodeId?: string;
    toEdgeId?: string;
    distanceMeter?: number;
    markerPoint?: [number, number];
    reason?: string;
  };
}

export interface BridgePayload {
  summary?: {
    componentCount?: number;
    endpointCount?: number;
    bridgeCandidateCount?: number | null;
    visibleBridgeCandidateCount?: number;
    bridgeMaxDistanceMeter?: number;
    bridgeAutoDistanceMeter?: number;
  };
  bbox?: [number, number, number, number] | null;
  bridges: {
    type: "FeatureCollection";
    features: BridgeFeature[];
  };
}

export interface RoadAttributeFeature {
  type: "Feature";
  bbox?: [number, number, number, number];
  geometry: {
    type: "LineString";
    coordinates: Array<[number, number]>;
  };
  properties: {
    handoffEdgeId: string;
    sourceId: string;
    districtGu: string;
    name?: string;
    roadName?: string;
    roadDivisionLabel?: string;
    pavementQualityLabel?: string;
    surfaceType?: string;
    laneCount?: number | null;
    widthMeter?: number | null;
    widthLevel?: string;
    widthLevelLabel?: string;
    slopeMean?: number | null;
    slopeMax?: number | null;
    slopeLevel?: string;
    slopeRange?: string;
    slopeLevelLabel?: string;
    riskLevel?: string;
    ufid?: string;
  };
}

export interface RoadAttributePayload {
  summary?: {
    roadAttributeCount?: number;
    guRoadAttributeCount?: number;
    visibleRoadAttributeCount?: number;
  };
  csv?: {
    roadAttributeCsv?: string;
  };
  bbox?: [number, number, number, number] | null;
  roadAttributes: {
    type: "FeatureCollection";
    features: RoadAttributeFeature[];
  };
}

export interface FacilityFeature {
  type: "Feature";
  bbox?: [number, number, number, number];
  geometry: {
    type: "Point";
    coordinates: [number, number];
  };
  properties: {
    placeId: string;
    name: string;
    category: PlaceCategory;
    address: string;
    providerPlaceId?: string;
  };
}

export interface FacilityPayload {
  summary?: {
    facilityCount?: number;
    visibleFacilityCount?: number;
    providerPlaceIdCount?: number;
    categoryCounts?: Record<string, number>;
    visibleCategoryCounts?: Record<string, number>;
  };
  csv?: {
    facilityCsv?: string;
  };
  bbox?: [number, number, number, number] | null;
  facilities: {
    type: "FeatureCollection";
    features: FacilityFeature[];
  };
  areaBoundary?: AreaBoundaryFeature | null;
}

export type ReferenceLayerKey = "roadAttributes" | "stairs" | "audioSignals" | "brailleBlocks";

export interface ReferencePointFeature {
  type: "Feature";
  bbox?: [number, number, number, number];
  geometry: {
    type: "Point";
    coordinates: [number, number];
  };
  properties: {
    sourceId: string;
    layerType: Exclude<ReferenceLayerKey, "roadAttributes">;
    label: string;
    state?: string;
    distanceMeter?: number | null;
    matchConfidence?: string;
  };
}

export interface ReferencePointPayload {
  summary?: {
    referencePointCount?: number;
    visibleReferencePointCount?: number;
    stateCounts?: Record<string, number>;
    visibleStateCounts?: Record<string, number>;
  };
  bbox?: [number, number, number, number] | null;
  points: {
    type: "FeatureCollection";
    features: ReferencePointFeature[];
  };
}

export type EditAction =
  | {
      action: "add_segment";
      segmentType: EditableSegmentType;
      geom: { type: "LineString"; coordinates: Array<[number, number]> };
      fromNode?: EditNodeRef;
      toNode?: EditNodeRef;
    }
  | { action: "delete_segment"; edgeId: string | number; reason?: string }
  | { action: "delete_node"; vertexId: string | number; reason?: string };

export type EditNodeRef =
  | {
      mode: "existing";
      vertexId: string | number;
      sourceNodeKey?: string | null;
      geom: { type: "Point"; coordinates: [number, number] };
      snapDistanceMeter: number;
    }
  | {
      mode: "new";
      tempNodeId: string;
      sourceNodeKey: string;
      geom: { type: "Point"; coordinates: [number, number] };
      snapDistanceMeter: null;
    };

export interface ManualEditDocument {
  version: "ADMIN-draft-v1";
  assignmentId: string;
  gu: string;
  dong: string;
  role: AdminRole;
  createdAt: string;
  edits: EditAction[];
}

export interface RoadNetworkEditApplyResponse {
  addedSegments: number;
  skippedSegments?: number;
  deletedSegments: number;
  createdNodes: number;
  snappedNodes: number;
  removedOrphanNodes: number;
  createdSegmentFeatures: number;
  updatedSegmentAttributes: number;
  addedEdgeIds: number[];
  deletedEdgeIds: number[];
  createdNodeIds: number[];
  snappedNodeIds: number[];
}

export type RoadNetworkEditJobStatus = "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED";

export interface RoadNetworkEditJobResponse {
  jobId: number;
  status: RoadNetworkEditJobStatus;
  totalEdits: number;
  processedEdits: number;
  message: string;
  result?: RoadNetworkEditApplyResponse | null;
}

export interface PlaceAccessibilityFeature {
  featureType: AccessibilityFeatureType;
  isAvailable: boolean;
}

export interface AdminPlaceDetailResponse {
  placeId: number;
  name: string;
  category: PlaceCategory;
  address: string | null;
  point: GeoPoint;
  providerPlaceId: string | null;
  accessibilityFeatures: PlaceAccessibilityFeature[];
}

export interface AdminPlaceUpdateRequest {
  name?: string | null;
  category?: PlaceCategory | null;
  address?: string | null;
  point?: GeoPoint | null;
  providerPlaceId?: string | null;
}

export type HazardReportStatus = "PENDING" | "APPROVED" | "REJECTED";

export type HazardReportType =
  | "STAIRS_STEP"
  | "BRAILLE_BLOCK"
  | "SIDEWALK_MISSING"
  | "RAMP"
  | "SIDEWALK_WIDTH"
  | "OTHER_OBSTACLE";

export interface GeoPoint {
  lat: number;
  lng: number;
}

export type AccessibilityState = "YES" | "NO" | "UNKNOWN";
export type WidthState = "ADEQUATE_150" | "ADEQUATE_120" | "NARROW" | "UNKNOWN";
export type SurfaceState = "PAVED" | "UNPAVED" | "UNKNOWN";

export interface AdminRoadSegmentAttributesUpdateRequest {
  walkAccess?: AccessibilityState | null;
  brailleBlockState?: AccessibilityState | null;
  audioSignalState?: AccessibilityState | null;
  widthState?: WidthState | null;
  surfaceState?: SurfaceState | null;
  stairsState?: AccessibilityState | null;
  signalState?: AccessibilityState | null;
  applyRoutingImmediately?: boolean | null;
}

export type AdminRoutingApplyStatus = "PENDING" | "SKIPPED" | "APPLIED" | "APPLIED_WITH_WARNING" | "FAILED";

export interface AdminRoutingApplyStateResponse {
  routingApplyStatus: AdminRoutingApplyStatus;
  message: string | null;
  dirty: boolean;
  applying: boolean;
  lastAppliedAt: string | null;
}

export interface AdminRoadSegmentUpdateResponse {
  segment: SegmentFeature["properties"];
  routingApplyStatus: AdminRoutingApplyStatus;
  routingApplyMessage: string | null;
}

export type WalkRouteProfile =
  | "PEDESTRIAN_SAFE"
  | "PEDESTRIAN_FAST"
  | "VISUAL_SAFE"
  | "VISUAL_FAST"
  | "WHEELCHAIR_MANUAL_SAFE"
  | "WHEELCHAIR_MANUAL_FAST"
  | "WHEELCHAIR_AUTO_SAFE"
  | "WHEELCHAIR_AUTO_FAST";

export type AdminRouteProfileGroup = "PEDESTRIAN" | "VISUAL" | "WHEELCHAIR_MANUAL" | "WHEELCHAIR_AUTO";

export interface AdminRoutePreviewRequest {
  gu: string;
  dong: string;
  startPoint: GeoPoint;
  endPoint: GeoPoint;
  profileGroup: AdminRouteProfileGroup;
}

export interface AdminRoutePreviewItemResponse {
  profile: WalkRouteProfile;
  distanceMeter: number;
  durationSecond: number;
  estimatedTimeMinute: number;
  coordinates: GeoPoint[];
}

export interface AdminRoutePreviewResponse {
  safeRoute: AdminRoutePreviewItemResponse;
  fastRoute: AdminRoutePreviewItemResponse;
}

export interface AdminMeResponse {
  userId: string;
  role: AdminRole;
  permissions: string[];
}

export interface AdminUserResponse {
  userId: string;
  socialProvider: SocialProvider;
  socialProviderUserId: string;
  selectedPrimaryUserType: string;
  selectedMobilitySubtype: string | null;
  role: UserRole;
  createdAt: string;
  updatedAt: string;
}

export interface AdminUserListResponse {
  users: AdminUserResponse[];
}

export interface AdminAreaAssignmentListResponse {
  assignments: Assignment[];
}

export interface AdminAuditLog {
  logId: number;
  actorUserId: string;
  action: string;
  targetType: string;
  targetId: string | null;
  gu: string | null;
  dong: string | null;
  summary: string;
  beforeJson: unknown | null;
  afterJson: unknown | null;
  createdAt: string;
}

export interface AdminAuditLogListResponse {
  logs: AdminAuditLog[];
  size: number;
  nextCursor: number | null;
  hasNext: boolean;
}

export interface AdminDashboardSummaryResponse {
  period: {
    from: string;
    to: string;
  };
  users: {
    totalUsers: number;
    newUsers: number;
    adminUsers: number;
    routeActiveUsers7d: number;
    userTypeCounts: Record<string, number>;
  };
  routes: {
    totalNavigationSessions: number;
    navigationStarted: number;
    navigationCompleted: number;
    navigationCompletionRate: number;
    averageNavigationMinutes: number;
    rerouteCount: number;
    activeRouteSessions: number;
    averageRouteSpeedMps: number;
    dailyMovement: AdminDashboardDailyMovementMetric[];
  };
  reports: {
    totalReports: number;
    newReports: number;
    pendingReports: number;
    approvedReports: number;
    rejectedReports: number;
    reportTypeCounts: Record<string, number>;
  };
  dataQuality: {
    roadSegments: number;
    facilities: number;
    roadNetworkAssignments: number;
    facilityAssignments: number;
    roadNetworkCompletedRate: number;
    facilityCompletedRate: number;
  };
  operations: {
    recentAuditLogs: AdminAuditLog[];
    recentReports: AdminDashboardRecentReport[];
  };
  telemetry: {
    enabled: boolean;
    message: string;
  };
}

export interface AdminDashboardDailyMovementMetric {
  date: string;
  routeCount: number;
  activeUserCount: number;
}

export interface AdminDashboardRecentReport extends GeoPoint {
  reportId: number;
  reportType: HazardReportType;
  description: string | null;
  status: HazardReportStatus;
  createdAt: string;
}

export interface AdminDashboardBottleneckResponse {
  period: {
    from: string;
    to: string;
  };
  telemetryBased: boolean;
  source: string;
  topBottlenecks: AdminDashboardTopBottleneck[];
  hotspots: AdminDashboardBottleneckHotspot[];
  routeSegments: AdminDashboardBottleneckRouteSegment[];
}

export interface AdminDashboardTopBottleneck {
  rank: number;
  id: string;
  name: string;
  averageSpeedMps: number;
  reportCount: number;
  sampleCount: number;
}

export interface AdminDashboardBottleneckHotspot extends GeoPoint {
  id: string;
  name: string;
  averageSpeedMps: number;
  reportCount: number;
  sampleCount: number;
}

export interface AdminDashboardBottleneckRouteSegment {
  id: string;
  name: string;
  points: GeoPoint[];
  averageSpeedMps: number;
  reportCount: number;
  sampleCount: number;
}

export type AdminRouteStatsMobilityFilter =
  | "ALL"
  | "MOBILITY_SUPPORT"
  | "POWER_WHEELCHAIR"
  | "MANUAL_WHEELCHAIR"
  | "VISUAL_IMPAIRMENT";

export type AdminRouteStatsTimeGranularity = "DAILY" | "WEEKLY" | "MONTHLY";
export type AdminRouteStatsMapMode = "HEATMAP" | "WAYPOINT" | "DENSITY";

export interface AdminRouteStatsResponse {
  period: {
    from: string;
    to: string;
  };
  summary: {
    totalTrips: number;
    metricLabel: string;
  };
  filters: {
    mobilityOptions: Array<{
      value: AdminRouteStatsMobilityFilter;
      label: string;
    }>;
    timeGranularityOptions: Array<{
      value: AdminRouteStatsTimeGranularity;
      label: string;
    }>;
    defaults: {
      mobility: AdminRouteStatsMobilityFilter;
      timeGranularity: AdminRouteStatsTimeGranularity;
    };
  };
  map: {
    title: string;
    legendMinLabel: string;
    legendMaxLabel: string;
    metricOptions: Array<{
      value: string;
      label: string;
    }>;
    selectedMetric: string;
    modeOptions: Array<{
      value: AdminRouteStatsMapMode;
      label: string;
    }>;
    selectedMode: AdminRouteStatsMapMode;
    showDistrictBoundary: boolean;
    hotspots: AdminDashboardBottleneckHotspot[];
    routeSegments: AdminDashboardBottleneckRouteSegment[];
  };
  topRoutesDefinition: string;
  topRoutes: Array<{
    rank: number;
    name: string;
    routeCount: number;
    share: number;
    tone: "danger" | "hot" | "warm" | "clear";
  }>;
  typeBreakdown: Array<{
    label: string;
    count: number;
    share: number;
    color: string;
  }>;
  hourlyHeatmap: {
    title: string;
    helperText: string;
    xLabels: string[];
    yLabels: string[];
    values: number[][];
  };
  speedTrend: {
    labels: string[];
    series: Array<{
      label: string;
      color: string;
      values: number[];
    }>;
  };
  distanceDistribution: {
    buckets: string[];
    series: Array<{
      label: string;
      color: string;
      count: number[];
      share: number[];
    }>;
  };
  averageDistance: Array<{
    label: string;
    kilometer: number;
    color: string;
  }>;
  infoItems: Array<{
    label: string;
    value: string;
  }>;
}

export interface AdminBottleneckMonitoringResponse {
  title: string;
  subtitle: string;
  dateRangeLabel: string;
  exportLabel: string;
  summaryCards: Array<{
    label: string;
    valueLabel: string;
    deltaLabel: string;
    comparisonLabel: string;
    tone: "danger" | "warning" | "success";
    icon: "alert" | "fire" | "users" | "check";
  }>;
  trend: {
    labels: string[];
    series: Array<{
      label: string;
      color: string;
      values: number[];
    }>;
    maxValue: number;
  };
  distribution: {
    totalCount: number;
    items: Array<{
      label: string;
      count: number;
      share: number;
      color: string;
    }>;
  };
  map: {
    hotspots: AdminDashboardBottleneckHotspot[];
    routeSegments: AdminDashboardBottleneckRouteSegment[];
  };
  table: {
    filters: {
      typeLabel: string;
      statusLabel: string;
      sortLabel: string;
      pageSizeLabel: string;
    };
    rows: Array<{
      rank: number;
      location: string;
      address: string;
      typeLabel: string;
      typeTone: "blue" | "green" | "orange" | "purple";
      affectedUsersLabel: string;
      statusLabel: string;
      statusTone: "danger" | "warning" | "neutral";
      reportedAt: string;
    }>;
    pagination: {
      currentPage: number;
      pages: number[];
    };
  };
  impactTop: {
    sortLabel: string;
    items: Array<{
      rank: number;
      location: string;
      affectedUsersLabel: string;
      statusLabel: string;
      statusTone: "danger" | "warning" | "neutral";
    }>;
  };
}

export interface AdminHazardReportSummary {
  reportId: number;
  reporterUserId: string;
  reportType: HazardReportType;
  reportPoint: GeoPoint;
  status: HazardReportStatus;
  createdAt: string;
  representativeImageUrl: string | null;
  latestRouteReview?: AdminHazardRouteReview | null;
}

export interface AdminHazardReportListResponse {
  content: AdminHazardReportSummary[];
  size: number;
  nextCursor: number | null;
  hasNext: boolean;
}

export type AdminHazardRouteReviewIntent = "APPROVE" | "RESTORE";
export type AdminHazardRouteReviewStage = "IN_PROGRESS" | "COMPLETED";

export interface AdminHazardRouteReviewSegmentDraft {
  edgeId: number;
  walkAccess?: AccessibilityState | null;
  brailleBlockState?: AccessibilityState | null;
  audioSignalState?: AccessibilityState | null;
  widthState?: WidthState | null;
  surfaceState?: SurfaceState | null;
  stairsState?: AccessibilityState | null;
  signalState?: AccessibilityState | null;
}

export interface AdminHazardRouteReview {
  reviewId: number;
  reportId: number;
  intent: AdminHazardRouteReviewIntent;
  stage: AdminHazardRouteReviewStage;
  reportStatus: HazardReportStatus;
  reviewerUserId: string;
  gu: string;
  dong: string;
  selectedSegmentEdgeId: number | null;
  startedAt: string;
  updatedAt: string;
  completedAt: string | null;
  segmentDrafts: AdminHazardRouteReviewSegmentDraft[];
  routingApplyStatus?: AdminRoutingApplyStatus | null;
  routingApplyMessage?: string | null;
}

export interface StartAdminHazardRouteReviewRequest {
  intent: AdminHazardRouteReviewIntent;
}

export interface UpdateAdminHazardRouteReviewRequest {
  selectedSegmentEdgeId?: number | null;
  segmentDrafts?: AdminHazardRouteReviewSegmentDraft[];
}

export interface AdminHazardReportDetail {
  reportId: number;
  reporterUserId: string;
  reportType: HazardReportType;
  description: string | null;
  reportPoint: GeoPoint;
  status: HazardReportStatus;
  createdAt: string;
  imageUrls: string[];
  latestRouteReview?: AdminHazardRouteReview | null;
}

export interface AdminHazardReportStatusResponse {
  reportId: number;
  status: HazardReportStatus;
}

export interface AdminHazardReportDeleteResponse {
  reportId: number;
}
