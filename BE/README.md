<div align="center">

# 부산EumGil Backend

### Busan EumGil API Server

**부산 이동 약자를 위한 무장애 길찾기 서비스 Backend**

<br>

[![Java](https://img.shields.io/badge/Java-21-007396?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.13-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Security](https://img.shields.io/badge/Spring_Security-6-6DB33F?style=flat-square&logo=springsecurity&logoColor=white)](https://spring.io/projects/spring-security)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-PostGIS-336791?style=flat-square&logo=postgresql&logoColor=white)](https://postgis.net/)
[![Redis](https://img.shields.io/badge/Redis-Cache-DC382D?style=flat-square&logo=redis&logoColor=white)](https://redis.io/)
[![GraphHopper](https://img.shields.io/badge/GraphHopper-Routing-77B829?style=flat-square)](https://www.graphhopper.com/)

</div>

---

> 프로젝트 전체 개요, 운영 구조, Git/Jira 컨벤션은 [README.md](../README.md)를 기준으로 봅니다.

## 프로젝트 소개

**부산EumGil Backend**는 부산 지역 이동 약자를 위한 길찾기, 장소 제공, 위험 제보, 관리자 반영 워크플로를 담당하는 API 서버입니다.

이 서버는 단순 CRUD보다는 다음과 같은 핵심 로직을 구현하는 데 초점을 둡니다.

- 사용자 유형에 따라 다른 보행 프로필을 적용하는 경로 탐색
- GraphHopper, ODsay, 부산 BIMS, 카카오 로컬 API를 조합한 위치·경로·대중교통 데이터 파이프라인
- 위험 제보 접수 이후 이미지 업로드 검증, 멱등 처리, 지도 마커 노출, 재탐색까지 이어지는 제보 처리 흐름
- 관리자가 도로 세그먼트와 시설 메타데이터를 수정하고 GraphHopper 런타임에 반영하는 운영 워크플로

### 핵심 제공 기능

- **도보 경로 탐색**: SAFE/SHORTEST 후보를 사용자 이동 유형별 GraphHopper 프로필로 조회
- **대중교통 경로 탐색**: ODsay 경로 후보에 도보 구간 보정과 BIMS 실시간 도착 정보를 결합
- **경로 안내 이벤트 생성**: 횡단보도, 경사, 계단, 폭, 노면, 방향 전환을 guidance event와 badge로 변환
- **위험 제보 처리**: presigned URL 발급, 이미지 소유권 검증, 멱등 제보 생성, 승인 마커 제공
- **위험 제보 기반 재탐색**: 신고 지점을 회피 영역으로 변환해 기존 경로를 우회 경로로 재구성
- **장소·시설 제공**: 내부 시설 DB, 카카오 장소 검색, 버스/지하철 마스터 데이터를 합쳐 상세 응답 제공
- **관리자 편집 반영**: 도로/시설 수정, 감사 로그 기록, routing override 저장, GraphHopper reload 연계

### 처리 흐름

```text
클라이언트 요청
  -> JWT 인증 / 사용자 프로필 조회
  -> 도메인 서비스에서 좌표·권한·범위 검증
  -> 외부 API / PostGIS / Redis / S3 조합 처리
  -> API 응답 DTO 조립
  -> 필요 시 route session / routing override / audit log 저장
```

---

## 주요 문서

- API 명세: [../Docs/API/](../Docs/API/)
- ERD: [../Docs/ERD/ERD_v4.md](../Docs/ERD/ERD_v4.md)
- 기술 문서: [docs/README.md](docs/README.md)
- Backend convention: [../Docs/skills/backend/backend-convention.md](../Docs/skills/backend/backend-convention.md)
- Layer / package convention: [../Docs/skills/backend/layer-package-convention.md](../Docs/skills/backend/layer-package-convention.md)
- API response / error convention: [../Docs/skills/backend/api-response-error-convention.md](../Docs/skills/backend/api-response-error-convention.md)
- Config / external convention: [../Docs/skills/backend/config-external-convention.md](../Docs/skills/backend/config-external-convention.md)
- 프로젝트 전체 README: [../README.md](../README.md)

---

## 기술 스택

| 분류 | 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5, Spring Web, Spring Validation |
| Security | Spring Security, JWT |
| Data | Spring Data JPA, PostgreSQL, PostGIS, Hibernate Spatial |
| Cache / Session | Redis |
| Routing | GraphHopper |
| External APIs | Kakao Local API, ODsay, Busan BIMS, AI Voice Analysis API |
| File Storage | AWS S3 SDK, MinIO-compatible presigned upload |
| Docs / Observability | Springdoc OpenAPI, Actuator, Prometheus |
| Quality | JUnit 5, Spring Boot Test, Spotless, Checkstyle |

---

## 백엔드 구조

```text
BE/
├── src/main/java/com/ssafy/e102/
│   ├── domain/
│   │   ├── auth/        # 소셜 로그인, 토큰, 쿠키, 인증 흐름
│   │   ├── user/        # 사용자 프로필, 이동 유형
│   │   ├── route/       # 도보/대중교통 경로 탐색, 세션, 평가, 재탐색
│   │   ├── place/       # 장소 검색, 시설 상세, 즐겨찾기, 음성 분석
│   │   ├── report/      # 위험 제보, 이미지 업로드, 승인 마커, 제보 기반 우회
│   │   └── admin/       # 관리자 지도 편집, 감사 로그, routing 반영
│   └── global/
│       ├── external/    # Kakao, ODsay, BIMS, GraphHopper, AI client
│       ├── security/    # JWT filter, principal, security config
│       ├── response/    # ApiResponse, ErrorResponse
│       ├── exception/   # 공통 예외/에러 코드
│       └── geo/         # 좌표 변환, 거리 계산
├── src/main/resources/
│   ├── application.yml
│   ├── application-local.yml
│   ├── application-dev.yml
│   ├── application-prod.yml
│   └── schema.sql
├── build.gradle
└── gradlew / gradlew.bat
```

### 디렉터리 책임

- `domain/route`
  도보, 대중교통, route session, route rating, reroute 로직을 담당합니다.
- `domain/place`
  내부 시설 DB와 외부 장소 API를 결합해 검색/상세 응답을 제공합니다.
- `domain/report`
  위험 제보 생성, 이미지 업로드, 승인 마커, 제보 기반 우회 흐름을 담당합니다.
- `domain/admin`
  운영자가 도로/시설 데이터를 편집하고 GraphHopper에 반영하는 워크플로를 담당합니다.
- `global/external`
  외부 API 응답을 도메인 서비스가 직접 알지 않도록 provider client를 분리합니다.
- `global/security`
  stateless JWT 인증과 권한 체크를 담당합니다.

---

## 핵심 구현

### 1. 사용자 유형 기반 도보 경로 탐색

- `WalkRouteProfileService`가 `PrimaryUserType`과 `MobilitySubtype`을 `WalkRouteProfile`로 매핑합니다.
- 저시력 사용자와 휠체어 사용자에게 서로 다른 SAFE/FAST 프로필을 적용합니다.
- `WalkRouteSearchService`는 부산 서비스 영역 여부와 출발지-도착지 최소 거리(20m)를 먼저 검증합니다.
- `WalkRouteGraphHopperSearchService`는 SAFE와 SHORTEST를 고정 순서로 각각 조회해 후보 경로를 구성합니다.
- `GraphHopperRouteClient`는 GraphHopper `/route`를 호출하면서 `edge_id`, `segment_type`, `avg_slope_percent`, `stairs_state`, `width_state` 같은 path detail을 함께 받아옵니다.
- `walk_access=NO`가 포함된 경로는 즉시 제외하고, active endpoint 실패 시 previous endpoint로 fallback 합니다.
- 검색 결과는 `RouteSearchCacheService`를 통해 Redis에 30분 TTL로 저장해 이후 경로 선택과 재조회에 재사용합니다.

### 2. 경로 응답을 안내 이벤트 중심으로 재조립

- `WalkRoutePayloadService`는 GraphHopper geometry를 API 응답용 `RouteSummaryResponse`와 `RouteLegResponse`로 변환합니다.
- 단순 선형 geometry만 넘기지 않고, 횡단보도·경사·계단·좁은 보도·비포장 구간을 `guidanceEvents`와 `badges`로 가공합니다.
- 경사도는 사용자 프로필별 임계치가 다르게 적용됩니다.
- 방향 전환은 `RouteTurnInstructionService`가 geometry 각도를 직접 계산해 LEFT/RIGHT를 판정합니다.
- 횡단보도 인접 구간의 짧은 지그재그 회전은 제거하고, 횡단보도 직후 직진 안내를 보강해 음성/시각 안내 품질을 높였습니다.

### 3. 대중교통 경로 탐색과 실시간 정보 보강

- `TransitRouteSearchService`는 ODsay 대중교통 후보를 먼저 조회하고 추천/최소 환승/최소 보행 기준으로 shortlist를 만듭니다.
- ODsay lane geometry가 없으면 즉시 조회하고, `OdsayLoadLaneStore`에 저장해 malformed 데이터를 복구 가능한 형태로 캐시합니다.
- 각 도보 leg는 GraphHopper로 다시 계산해 동일한 accessibility guidance 체계를 유지합니다.
- 버스 leg는 부산 BIMS 도착 정보를 비동기로 모아 lane option과 low-floor 여부를 보강합니다.
- 지하철 leg는 역 엘리베이터 메타데이터와 시간표를 결합해 환승/도착 안내를 구성합니다.
- 최종 경로 후보와 transit metadata 역시 Redis에 저장해 refresh와 reroute에 사용합니다.

### 4. route session 기반 안내 유지와 재탐색

- 사용자가 경로를 선택하면 `RouteSessionCommandService`가 route snapshot을 DB에 저장하고 active session을 1개로 정규화합니다.
- 재탐색은 검색 결과를 다시 만드는 방식이 아니라 저장된 route snapshot을 복원한 뒤 현재 위치를 route geometry에 투영해서 처리합니다.
- `RouteProjectionGeometryService`는 현재 좌표를 경로 선분에 투영해 진행 위치와 거리 오프셋을 계산합니다.
- 이 구조 덕분에 경로 선택 후 안내 종료, 이탈 재탐색, 제보 기반 우회가 같은 session 모델을 공유합니다.

### 5. 위험 제보 생성 파이프라인

- `HazardReportImageUploadService`는 이미지 업로드 전에 presigned PUT URL을 발급하고, object key가 업로더 사용자 prefix와 일치하는지 검증합니다.
- 업로드 포맷은 JPEG/PNG/WEBP/HEIC/HEIF로 제한하고, 최대 5장, 최대 10MB를 강제합니다.
- `HazardReportService`는 제보 생성 시 `Idempotency-Key`를 지원해 중복 요청을 24시간 동안 같은 결과로 귀결시킵니다.
- 멱등 키가 같은데 요청 본문 hash가 다르면 conflict로 처리해 중복 제보와 잘못된 재시도를 구분합니다.
- 제보 좌표는 카카오 reverse geocode로 주소를 보강하고, 승인 전에는 `PENDING` 상태로 저장합니다.
- 승인된 제보 마커 조회는 bbox 대각선 길이를 2km 이하로 제한해 과도한 지도 요청을 막고, 썸네일 presigned GET URL을 함께 내려줍니다.

### 6. 위험 제보 기반 우회 경로 생성

- `HazardReportRerouteService`는 제보 지점을 현재 경로 위에 투영한 뒤, 세그먼트 방향을 기준으로 회피 polygon을 생성합니다.
- `HazardReportAvoidAreaBuilder`는 제보 지점 뒤 10m, 앞 2m, 좌우 5m 폭의 회피 영역을 만듭니다.
- `HazardReportRerouteCustomModelFactory`는 GraphHopper custom model에 `in_hazard_area -> multiply_by 0` 규칙을 넣어 해당 영역을 사실상 통과 불가로 만듭니다.
- 도보 경로는 전체를 다시 계산하고, 대중교통 경로는 현재 활성 walk leg만 다시 계산한 뒤 나머지 transit leg와 재조립합니다.
- 재조립 시 leg별 guidance event의 거리/시간 오프셋을 다시 계산해 전체 route 응답의 일관성을 유지합니다.

### 7. 장소 검색과 시설 제공 로직

- `PlaceService`는 카카오 키워드 검색 결과를 부산 주소만 남기도록 필터링하고, 내부 `places` 테이블과 provider place id로 매칭합니다.
- 장소 검색 cursor는 카카오 page를 base64로 감싼 내부 cursor로 변환해 노출합니다.
- 주변 시설 조회는 `PlaceRepository.findPlaceMarkerIds`에서 PostGIS `ST_DWithin`과 feature/category 조건을 함께 사용해 marker ID를 먼저 좁힙니다.
- 장소 상세는 내부 시설이면 accessibility feature와 bookmark 상태를 함께 반환하고, 외부 POI이면 provider 기반 bookmark target id를 생성합니다.
- 버스 정류장과 지하철역은 일반 POI와 따로 판별해 BIMS 실시간 도착 정보, 지하철 시간표, 엘리베이터/접근성 정보를 결합한 상세 응답을 제공합니다.
- 내부 시설이 없더라도 외부 POI 검색 실패 시 건물명/주소 fallback을 수행해 클릭 상세 응답의 복원력을 높였습니다.

### 8. 관리자 도로/시설 편집과 GraphHopper 반영

- `AdminMapService`는 `gu/dong` 범위와 반경 clip을 조합해 도로망과 시설을 GeoJSON 형태로 제공합니다.
- 도로 편집은 `RoadSegment`의 보행 가능 여부, 점자블록, 음향신호기, 폭, 노면, 계단, 신호 상태를 optimistic locking 기반으로 갱신합니다.
- routing에 영향을 주는 속성은 `RoutingSegmentOverride`로 별도 저장해 GraphHopper 런타임 반영 대상만 분리합니다.
- 모든 수정은 `AdminAuditLogService`로 before/after를 남겨 운영 추적성을 확보합니다.
- `AdminRoutingApplyService`는 dirty state, applying lock, stale lock recovery를 관리하면서 GraphHopper reload를 1회 실행합니다.
- GraphHopper endpoint는 blue/green slot과 previous slot을 함께 관리해 reload 직후나 장애 상황에서도 fallback 가능합니다.

### 9. 제보 승인과 운영 워크플로 연계

- `AdminHazardRouteReviewService`는 제보 승인 전 segment draft를 작성하고, 승인 시 draft를 실제 도로 속성에 반영합니다.
- 승인 intent와 restore intent를 분리해 이미 승인된 제보의 복구 워크플로까지 지원합니다.
- route review에서 선택한 세그먼트가 editable area 밖이면 즉시 차단합니다.
- 승인 완료 시 hazard status 갱신과 routing apply pending 상태 갱신이 함께 이루어져 운영 흐름이 끊기지 않습니다.

### 10. 인증, 응답 규약, 운영성

- 인증은 `SecurityConfig`의 stateless JWT filter 체계로 구성되어 있고, `places`, `routes`, `hazard-reports`, `admin` API를 역할 기반으로 보호합니다.
- 응답은 `ApiResponse` / `ErrorResponse` 규약으로 통일하고, 도메인별 `ErrorCode` enum으로 비즈니스 예외를 관리합니다.
- profile은 `local`, `dev`, `prod`로 분리되어 있으며, GraphHopper, Redis, Kakao, ODsay, BIMS, S3/MinIO를 환경변수로 주입합니다.
- `/actuator/health`, `/actuator/prometheus`를 통해 상태와 메트릭을 노출합니다.

---

## 현재 아키텍처 결정

### 계층 구조

- Controller는 인증 주체와 요청 DTO만 받고, 실제 규칙은 domain service에서 처리합니다.
- 외부 API 응답 스키마는 `global.external`에서 흡수하고, domain service는 정제된 모델만 사용합니다.
- 경로 검색 결과는 Redis cache, 선택된 경로는 DB session snapshot으로 나눠 저장합니다.
- 공간 질의는 PostGIS, 런타임 경로 계산은 GraphHopper로 역할을 분리합니다.

### 데이터/운영 전략

- 도로 세그먼트와 시설은 PostGIS geometry 컬럼을 기준으로 조회합니다.
- 사용자 제보 이미지는 presigned URL 기반으로 직접 업로드하고, 서버는 object key 검증과 URL 발급만 담당합니다.
- 운영자가 도로 속성을 수정해도 즉시 GraphHopper에 반영하지 않고 dirty state를 쌓은 뒤 apply 시점에 반영합니다.
- 제보 승인과 route review는 감사 로그와 area scope 검증을 포함한 운영 워크플로로 관리합니다.

---

## 시작하기

### 1. 이동

```bash
git clone <repository-url>
cd S14P31E102/BE
```

### 2. 개발 환경

- JDK 21
- PostgreSQL + PostGIS
- Redis
- GraphHopper runtime
- Gradle wrapper

### 3. 설정 파일

프로필은 `application-local.yml`, `application-dev.yml`, `application-prod.yml`로 분리됩니다.

주요 환경 변수:

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`
- `JWT_SECRET`
- `KAKAO_REST_API_KEY`
- `ODSAY_API_KEY`
- `BUSAN_BIMS_SERVICE_KEY_DECODING`
- `GRAPHHOPPER_BASE_URL` 또는 blue/green 관련 `GRAPHHOPPER_*`
- `REPORT_IMAGE_STORAGE_*` 또는 `S3_*` / `MINIO_*`

### 4. 실행

```bash
./gradlew bootRun
```

Windows:

```powershell
.\gradlew.bat bootRun
```

### 5. 테스트

```bash
./gradlew test
```

Windows:

```powershell
.\gradlew.bat test
```

### 6. API 문서 / 헬스체크

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Actuator Health: `http://localhost:8080/actuator/health`
- Prometheus: `http://localhost:8080/actuator/prometheus`
