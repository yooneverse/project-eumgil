# Project

## Identity

- Name: 부산이음길
- English name: Busan EumGil
- Repository: `S14P31E102`
- Repository role: SSAFY team project repository with a shared AI harness
- Product definition: 부산 지역 이동 약자를 위한 무장애 길찾기 모바일 서비스
- Primary platform: Android app MVP with backend APIs and spatial data support
- Primary users: 시각장애인, 휠체어 사용자, 기타 보행약자
- Primary region: 부산

## Problem

부산은 언덕, 계단, 단차, 복잡한 지형이 많아 휠체어 이용자, 고령자, 유아차 동반 보호자, 일시적 이동 불편자, 시각장애인이 안전하게 이동하기 어렵다. 기존 범용 길찾기 서비스는 일반 보행자 기준 최단 경로를 제공하며, 경사도, 계단, 엘리베이터, 점자블록, 장애물 제보 같은 접근성 정보를 충분히 반영하지 못한다.

부산이음길은 이동 약자가 처음 가는 장소의 장벽 정보를 사전에 확인하고, 계단과 급경사를 피하며, 접근성 시설과 음성 안내를 활용해 더 독립적으로 이동할 수 있게 하는 것을 목표로 한다.

## Target Users

- 시각장애인: TTS 안내, 점자블록 정보, 큰 버튼, 간결한 화면, 음성 기반 목적지 설정과 저시력/시각지원 전용 흐름이 중요하다.
- 보행약자: 휠체어 이용자, 고령자, 유아차 동반 보호자, 일시적 이동 불편자를 포함한다.
- 일반 보행자는 핵심 대상이 아니다. 범용 길찾기보다 교통약자 전용 접근성 길찾기에 초점을 둔다.

## Product Scope

### MVP shared scope

- 인증 기반 앱 진입과 사용자 유형 기반 온보딩
- 위치정보/개인정보 관련 동의 수집
- 현재 위치 기반 부산 지도 표시
- 베리어프리 시설 마커 표시와 시설 상세 조회
- 목적지 검색과 접근성 중심 경로 탐색
- 경로 결과 표시와 길안내
- 북마크와 자주 가는 길 관리
- 도로 상태 제보와 제보 내역 확인
- 저시력자 전용 화면군과 보행약자 공통 화면군 분리

### Expansion scope

- 사용자 동의 기반 경로 로그 수집
- 음성 검색, STT/LLM/TTS 기반 목적지 설정
- 경로 이탈 재탐색 고도화
- 저상버스, 지하철 엘리베이터, 두리발 등 대중교통/이동지원 연계
- Gemini API 기반 점자블록 판별
- 웨어러블, 커뮤니티, 추천, 다국어, iOS

## Product Principles

- Auth-backed production flow: production 기준 핵심 기능 진입은 인증 사용자 흐름을 전제로 한다. 게스트 모드와 `비로그인으로 계속`은 현재 기준 계약이 아니다.
- Accessibility-first: 최단 경로보다 안전하고 이동 가능한 경로를 우선한다.
- Busan-specific: 부산의 경사, 산복도로, 보도 부재 생활권, 골목길 특성을 데이터와 경로 비용에 반영한다.
- Public-data-driven: 공공데이터, OSM, 지형 데이터, 사용자 제보를 정제해 접근성 정보를 보강한다.
- Privacy-conscious: 위치, 경로 로그, 장애 유형 등 민감한 정보는 최소 수집, 동의, 보관 기준을 전제로 다룬다.
- Human-reviewable: 사용자 제보는 즉시 최종 반영하지 않고 검토 상태를 거쳐 지도 정보나 위험도에 반영한다.

## Core Domains

- 사용자 도메인: 인증, 프로필/사용자 유형, 약관 동의, 북마크, 자주 가는 길
- 장소 도메인: 장소 검색, 주변 시설 마커, 접근성 시설 정보
- 길안내 도메인: 보행 네트워크, 경로 옵션, 길안내 상태, 음성/시각지원 흐름
- 제보 도메인: hazard reports, report images, Slack 기반 검토 흐름
- 대중교통/확장 도메인: 저상버스, 정류장/노선/도착 정보, 지하철 엘리베이터, 외부 길찾기 후보
- 운영/인프라 도메인: dev/prod 분리, CI/CD, 모니터링, Blue/Green 운영

## Current Runtime Notes

- FE 현재 구현은 인증 gate, 프로필 완료 상태, 온보딩 상태, 사용자 유형에 따라 앱 시작 분기를 결정한다.
- 저시력자 경험은 보행약자 공통 화면의 스타일 변형이 아니라 별도 FE 화면군과 별도 하단 탭 흐름으로 관리한다.
- 공통 top-level 이동 구조, 사용자 노출 용어, 상세 route 계약은 `FE/app` 코드와 1차 FE 문서를 함께 확인해야 하며, shared 요약 문서인 이 파일에서 단정하지 않는다.
- 현재 구현 사실과 목표 FE 계약이 다를 때의 판정 규칙은 `.ai/DOCS.md`와 `.ai/LANES.md`를 따른다.

## Data And Routing Context

- 공간 데이터는 PostGIS를 기준으로 관리한다.
- 장소 검색은 카카오 Local API와 내부 장소/접근성 DB 매칭을 전제로 한다.
- 보행 네트워크는 OSM node/edge 구조를 정제해 GraphHopper 라우팅 그래프 원천으로 활용한다.
- 경사도 데이터는 부산 5m DEM 기반 PoC 결과를 토대로 road segment 단위에 slope를 부착하는 방향이다.
- 현재 경사도 PoC는 접근성 경로 추천 가능성을 확인했지만, 계단 데이터는 별도 확보와 결합이 필요하다.
- GraphHopper import/build 작업은 운영 서버에서 직접 수행하지 않고 Jenkins 또는 별도 배치에서 graph-cache 아티팩트로 생성해 배포한다.

## Architecture Context

- Backend API는 Spring Boot 기반으로 운영한다.
- Dev stack은 Docker Compose 기반이며 PostGIS, Redis, MinIO, backend를 포함한다.
- 운영 DB는 AWS RDS PostgreSQL/PostGIS를 전제로 한다.
- 캐시는 ElastiCache/Redis를 전제로 한다.
- 핵심 런타임은 WAS와 GraphHopper runtime이다.
- AWS 운영은 현재 EC2 2대 기준이며, 2026-05-20 실서버 확인 기준 S1은 dev/Jenkins/build/운영도구, S2는 primary prod runtime 역할을 가진다.
- 현재 prod blue/green은 EC2 서버 단위가 아니라 S2 내부 GraphHopper `blue/green` runtime slot 전환을 의미한다. ALB target group 기반 Blue/Green은 같은 VPC 또는 private routing 정리 이후 확장 옵션이다.
- Jenkins는 develop 배포, S2 prod 배포, GraphHopper refresh, smoke test 자동화를 담당한다.
- 모니터링은 CloudWatch를 1차 기준으로 두고, Grafana/Portainer/SonarQube/PLG는 보조 운영도구로 둔다.

## Source Documents

### Shared product documents

- `Docs/기획/2026-04-10 최종_프로젝트_기획서.md`
- `Docs/PRD/2026-04-09_부산이음길_PRD.md`
- `Docs/PRD/2026-04-14_기능명세서.md`
- `Docs/PRD/archive/2026-04_requirement_sources/2026-04-20_MON-01_연계_요구사항_정리.md`

### FE primary documents

- `FE/docs/2026-04-22_부산이음길_FE_화면_인벤토리_및_라우트_맵.md`
- `FE/docs/2026-04-22_부산이음길_FE_디자인_컨벤션.md`
- `FE/docs/2026-04-24_부산이음길_FE_접근성_정보_라벨_가이드.md`
- `FE/docs/2026-04-13_부산이음길_FE_코드_컨벤션.md`
- `FE/docs/2026-04-13_부산이음길_FE_컴포넌트_가이드.md`

### Backend and shared implementation documents

- `Docs/API/2026-04-12_API_전체_목록.md`
- `Docs/API/*/*_API_명세.md`
- `Docs/ERD/ERD_v4.md`
- `Docs/PoC/2026-04-21_부산_경사도_추출_정제_OSM_연계_통합_PoC.md`
- `Docs/인프라/2026-04-20_AWS_인프라_설계안.md`
- `Docs/인프라/2026-05-20_인프라_현재상태_및_운영_기준.md`
- `Docs/컨벤션/2026-04-09_Git_Jira_컨벤션.md`
- `Docs/컨벤션/2026-04-14_API_응답_코드_컨벤션.md`
- `Docs/skills/backend/`

Stage-specific loading rules and stale-document handling live in `.ai/DOCS.md`.

## FE/BE Boundary

- FE primary editable surfaces: `FE/app/`, `FE/docs/`, `FE/mockup/`
- BE primary editable surfaces: `BE/`, `Docs/API/`, `Docs/ERD/`, `Docs/skills/backend/`, `Docs/인프라/`
- FE 작업은 필요한 API/ERD 문서를 읽을 수 있지만 BE 구현 파일을 수정하지 않는다.
- BE 작업은 FE 화면 계약을 참고할 수 있지만 FE 구현 파일을 수정하지 않는다.
- Cross-lane follow-up은 shared 문서에 섞지 말고 handoff로 남긴다.

## Shared AI Harness Boundary

This file is shared team context. Keep project facts and team-wide assumptions here.

- Stage-specific docs loading rules belong in `.ai/DOCS.md`.
- FE/BE lane selection and boundary rules belong in `.ai/LANES.md`.
- Team workflow rules belong in `.ai/WORKFLOW.md`.
- Detailed harness structure belongs in `.ai/ARCHITECTURE.md`.
- Personal task notes and local sprint progress belong under `.ai/LOCAL/`.
