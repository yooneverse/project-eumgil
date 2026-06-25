# GraphHopper

## 선택 이유

- 보행 경로 계산 엔진을 내부 운영 환경에 올릴 수 있어 외부 경로 API 호출량과 비용에 종속되지 않는다.
- OSM 기반 경로 위에 서비스 전용 접근성 속성을 직접 실어 장애 유형별 프로파일을 만들 수 있다.
- custom model, path details, profile 분리 기능이 있어 SAFE/FAST 경로, 위험 구간 우회, 관리자 수정 반영에 모두 대응할 수 있다.

## 서비스에서 맡는 역할

- 도보 전용 경로 후보 생성
- 대중교통 경로의 도보 leg 재계산
- 위험 제보 승인 후 우회 경로 생성
- 관리자 도로 속성 수정 결과를 실제 라우팅 엔진에 반영

## 핵심 구현 방식

### 1. 사용자 유형별 프로파일 분리

- `WalkRouteProfileService`가 `PrimaryUserType`, `MobilitySubtype`를 `WalkRouteProfile`로 매핑한다.
- GraphHopper에는 `pedestrian_safe`, `pedestrian_fast`, `visual_safe`, `visual_fast`, `wheelchair_manual_safe`, `wheelchair_manual_fast`, `wheelchair_auto_safe`, `wheelchair_auto_fast` 같은 프로파일을 분리해 두었다.
- 같은 출발지와 목적지여도 사용자 유형에 따라 계단, 경사, 폭, 신호 설비를 다르게 해석한다.

### 2. Path details 기반 접근성 안내 재조립

- `GraphHopperRouteClient`는 `/route` 호출 시 geometry뿐 아니라 `edge_id`, `segment_type`, `avg_slope_percent`, `stairs_state`, `width_state`, `walk_access` 등 path details를 함께 요청한다.
- `WalkRoutePayloadService`와 `RouteTurnInstructionService`가 이 세부값을 guidance event, badge, turn instruction으로 다시 조립한다.
- 단순 선형 경로 반환이 아니라 "횡단보도 앞", "급경사", "계단 구간", "폭 협소" 같은 안내를 응답에 포함한다.

### 3. SAFE / SHORTEST 후보 비교

- `WalkRouteGraphHopperSearchService`는 안전 중심 후보와 최단 중심 후보를 고정 순서로 조회한다.
- GraphHopper가 path를 반환하면 route availability는 그 계산 결과를 source of truth로 사용한다.
- `path details.walk_access`는 base graph EV를 보여줄 수 있으므로 경로 가능 여부 판정에는 사용하지 않고, 응답/디버깅/안내 조립용으로만 유지한다.
- 결과는 `RouteSearchCacheService`를 통해 Redis에 보관해 이후 선택, 재탐색, 안내 갱신에 재사용한다.

### 4. 위험 제보 기반 우회

- `HazardReportAvoidAreaBuilder`가 제보 지점을 기준으로 작은 회피 polygon을 만든다.
- `HazardReportRerouteCustomModelFactory`가 custom model에 `in_hazard_area -> multiply_by 0` 규칙을 넣어 해당 구간을 사실상 통과 불가로 만든다.
- 도보 경로는 전체 재계산하고, 대중교통 경로는 현재 활성 도보 leg만 다시 계산해 기존 transit leg와 재조합한다.

### 5. 관리자 수정 반영

- 관리자 수정은 곧바로 운영 라우팅에 덮어쓰지 않고 `RoutingSegmentOverride`로 별도 저장한다.
- `AdminRoutingApplyService`가 dirty state를 모아 GraphHopper runtime reload 시점에 반영한다.
- 인프라 레벨에서는 blue/green slot과 previous slot을 유지해 재로딩 직후에도 이전 endpoint로 fallback 가능하게 구성했다.

## 예외 처리와 운영 이슈 대응

### endpoint 장애 대응

- active endpoint 호출 실패 시 previous endpoint로 fallback 한다.
- 라우팅 적용 작업은 Redis lock과 applying state를 이용해 동시 적용을 막고 stale lock recovery도 수행한다.

### 잘못된 경로 후보 차단

- overlay reopen이 적용된 edge는 graph rebuild 전에도 custom model 계산에서 통과할 수 있다.
- 이때 GraphHopper response detail에 base `walk_access=NO`가 남아 있어도 BE는 응답을 버리지 않는다.
- 최소 이동 거리, 유효 geometry, 필수 path details 누락 여부를 서비스 레벨에서 한 번 더 검증한다.

### 운영 반영 안정성

- 관리자 편집과 라우팅 반영을 분리해 지도 편집 중간 상태가 즉시 탐색 결과에 섞이지 않게 했다.
- reload 이후에도 previous slot을 유지해 장애 시 즉시 이전 런타임으로 우회 가능하다.

## 한계와 후속 과제

- OSM과 내부 세그먼트 품질에 따라 경사, 폭, 신호 설비 정확도가 달라진다.
- custom model 기반 우회는 국소 회피에는 강하지만, 복수 위험 구역이 넓게 겹칠 때는 우회 품질 조정이 더 필요하다.
- 관리자 반영 이후 자동 검증 시나리오와 회귀 라우팅 테스트를 더 강화할 여지가 있다.
