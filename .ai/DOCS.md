# Docs Source Map

`Docs/`와 `FE/docs/`는 역할이 다르다. FE 작업은 공통 제품 문서만 읽고 판단하지 말고, 실제 `FE/app` 구현과 1차 FE 계약 문서를 함께 읽어야 한다.

## Document freshness rules

- `.ai/PROJECT.md`는 요약 문서다. 작업 판단은 항상 실제 source documents로 내린다.
- FE lane에서는 `FE/app` 실제 구현과 1차 FE 문서를 함께 최상위 기준으로 둔다.
  - `FE/app`: 현재 구현 사실 확인
  - 1차 FE 문서: 목표 계약 확인
- `FE/mockup/`과 Figma handoff 같은 직접 산출물은 1차 FE 문서 다음 기준이다.
- `Docs/PRD/`, `Docs/기획/`, 필요한 `Docs/API/`는 FE 계약을 보강할 때만 읽는다.
- `FE/docs/archive/`, `FE/docs/debug-report/`, `FE/docs/todo/`, `FE/docs/sprint_backlog/` 같은 2차 정리 문서는 1차 FE 문서나 직접 산출물을 덮어쓰지 않는다.
- FE가 API 계약을 볼 때는 실제로 소비하는 도메인 문서만 선택적으로 읽는다. 기본 후보는 `장소_도메인`, `사용자_도메인`, `길안내_도메인`, 필요 시 `음성_도메인`이다.
- BE 작업은 `Docs/API/`, `Docs/ERD/`, `Docs/skills/backend/`, 실제 `BE/` 코드를 1차 기준으로 둔다.
- 코드, FE 문서, Figma/mockup, PRD/API가 충돌하면 아래 네 항목을 분리해 기록한다.
  - `current implementation fact`
  - `target contract`
  - `working basis for this task`
  - `Open questions` 또는 `Cross-Lane Handoff`

## Local stale document commands

작업 중 특정 문서를 이번 세션의 기준에서 제외해야 하면 아래 명령을 사용한다.

```bash
./.ai/scripts/docs-context.sh stale "Docs/기획/2026-04-09_화면명세서.md" "FE route map보다 오래된 화면 흐름"
./.ai/scripts/docs-context.sh status
./.ai/scripts/docs-context.sh clear "Docs/기획/2026-04-09_화면명세서.md"
```

- 제외 상태는 `.ai/LOCAL/DOCS/context-exclusions.json`에 저장되며 git에 올리지 않는다.
- 제외할 때의 파일 해시를 저장하므로 문서가 수정되면 제외 상태는 자동 만료된다.
- 활성 제외 문서는 구현, 계획, 리뷰에서 계약 근거로 쓰지 않는다. 필요하면 오래된 배경 문서로만 언급한다.

## FE source-of-truth order

1. 실제 `FE/app` 구현 + 1차 FE 계약 문서
2. 직접 FE 디자인 산출물
3. 공통 제품 문서: `Docs/PRD/`, `Docs/기획/`
4. FE가 실제로 소비하는 API 문서: `Docs/API/`
5. 필요 시 shared backend/data context: `Docs/ERD/`, `Docs/PoC/`, `Docs/인프라/`
6. 2차 정리 문서와 백로그 문서
7. `.ai/PROJECT.md` 요약

## BE source-of-truth order

1. 실제 `BE/` 구현 + `Docs/API/`
2. `Docs/ERD/ERD_v4.md`, `Docs/skills/backend/`
3. `Docs/컨벤션/`, `Docs/인프라/`
4. 공통 제품 문서: `Docs/PRD/`, `Docs/기획/`
5. `.ai/PROJECT.md` 요약

### Primary FE documents

- `FE/docs/2026-04-22_부산이음길_FE_화면_인벤토리_및_라우트_맵.md`
- `FE/docs/2026-04-22_부산이음길_FE_디자인_컨벤션.md`
- `FE/docs/2026-04-24_부산이음길_FE_접근성_정보_라벨_가이드.md`
- `FE/docs/2026-04-13_부산이음길_FE_코드_컨벤션.md`
- `FE/docs/2026-04-13_부산이음길_FE_컴포넌트_가이드.md`

### Representative FE code files for current behavior

- `FE/app/src/main/java/com/ssafy/e102/eumgil/app/navigation/Route.kt`
- `FE/app/src/main/java/com/ssafy/e102/eumgil/app/navigation/AppStartDestination.kt`
- `FE/app/src/main/java/com/ssafy/e102/eumgil/app/navigation/AppNavHost.kt`
- `FE/app/src/main/java/com/ssafy/e102/eumgil/app/navigation/MainNavGraph.kt`
- `FE/app/src/main/java/com/ssafy/e102/eumgil/app/navigation/OnboardingNavGraph.kt`
- `FE/app/src/main/java/com/ssafy/e102/eumgil/app/navigation/LowVisionNavGraph.kt`

### Primary BE documents

- `Docs/API/2026-04-12_API_전체_목록.md`
- `Docs/API/*/*_API_명세.md`
- `Docs/ERD/ERD_v4.md`
- `Docs/skills/backend/`
- `Docs/컨벤션/2026-04-14_API_응답_코드_컨벤션.md`
- `Docs/인프라/2026-04-20_AWS_인프라_설계안.md`
- `Docs/인프라/2026-04-23_Docker_운영_기준.md`
- `Docs/인프라/2026-04-23_인프라_스프린트_작업계획.md`

## Task-area source map

| Task area | Primary sources | Secondary sources | Notes |
|---|---|---|---|
| FE screen / route contract | `FE/app` navigation/screen code, `FE/docs/2026-04-22_부산이음길_FE_화면_인벤토리_및_라우트_맵.md` | `FE/docs/2026-04-21_S14P31E102-211_공통_UI_접근성_Figma_handoff.md`, `FE/mockup/`, `Docs/PRD/`, `Docs/기획/` | Code confirms current behavior. FE route map defines target contract. |
| FE UI / design | `FE/docs/2026-04-22_부산이음길_FE_디자인_컨벤션.md`, `FE/docs/2026-04-13_부산이음길_FE_컴포넌트_가이드.md` | Figma handoff, `FE/mockup/` | Design convention is not optional reference material. |
| FE accessibility / labels | `FE/docs/2026-04-24_부산이음길_FE_접근성_정보_라벨_가이드.md`, FE component guide | PRD, interview notes | Use fixed labels and reading order from FE docs. |
| FE code structure | `FE/docs/2026-04-13_부산이음길_FE_코드_컨벤션.md`, actual `FE/app` structure | FE route map | Treat stale examples as background only. |
| FE client API contract | Only the FE-consumed docs in `Docs/API/장소_도메인/`, `Docs/API/사용자_도메인/`, `Docs/API/길안내_도메인/`, optional `Docs/API/음성_도메인/` | `Docs/API/2026-04-12_API_전체_목록.md` | Pull in only what the touched FE flow actually needs. |
| BE API / domain | `Docs/API/`, actual `BE/` code | PRD, planning docs | BE lane primary scope. |
| Shared data / ERD | `Docs/ERD/ERD_v4.md` | API docs, actual entities | Use for shared model context, not as FE-first truth. |
| Shared route / spatial context | `Docs/PoC/2026-04-21_부산_경사도_추출_정제_OSM_연계_통합_PoC.md`, `Docs/API/길안내_도메인/2026-05-06_경로_API_명세.md` | `Docs/ERD/ERD_v4.md`, infra docs | Pull only when FE route behavior depends on backend route semantics. |
| Infra / release context | `Docs/인프라/` | actual ops config | Usually background for FE lane unless deployment behavior matters. |

## Common conventions

- 문서 작성 규칙: `Docs/컨벤션/2026-04-07_docs_작성_규칙.md`
- Git/Jira/MR 규칙: `Docs/컨벤션/2026-04-09_Git_Jira_컨벤션.md`
- API 응답 코드 규칙: `Docs/컨벤션/2026-04-14_API_응답_코드_컨벤션.md`
- FE 코드 컨벤션: `FE/docs/2026-04-13_부산이음길_FE_코드_컨벤션.md`
- FE 컴포넌트 가이드: `FE/docs/2026-04-13_부산이음길_FE_컴포넌트_가이드.md`
- FE 디자인 컨벤션: `FE/docs/2026-04-22_부산이음길_FE_디자인_컨벤션.md`
- FE 접근성 라벨 가이드: `FE/docs/2026-04-24_부산이음길_FE_접근성_정보_라벨_가이드.md`
- 백엔드 구현 컨벤션: `Docs/skills/backend/backend-convention.md`
- 백엔드 계층/패키지 규칙: `Docs/skills/backend/layer-package-convention.md`
- 백엔드 응답/예외 규칙: `Docs/skills/backend/api-response-error-convention.md`
- 설정/외부 연동 규칙: `Docs/skills/backend/config-external-convention.md`

## Single-repo lane rules

- Lane-specific work reads `.ai/LANES.md` first.
- FE-only work reads FE docs and `FE/app` first, then adds only the product/API docs it truly needs.
- BE-only work reads API/ERD/backend docs and `BE/` first.
- If current `FE/app` code differs from FE docs, never silently choose one.
  - Use code as `current implementation fact`.
  - Use 1차 FE docs as `target contract`.
- FE documentation work updates FE source documents first, then shared `.ai/` docs that point to them.
- BE documentation work updates `Docs/API/`, `Docs/ERD/`, `Docs/skills/backend/`, `Docs/인프라/` first, then shared `.ai/` docs that point to them.
- FE work does not edit BE implementation files. Backend follow-ups belong in `Cross-Lane Handoff`.

## Stage defaults

### Planning

- FE planning loads the five primary FE docs, the representative navigation files, and only the product/API docs needed for the requested flow.
- Cross-functional planning may add `Docs/ERD/ERD_v4.md`, route PoC, or infra docs, but only when the FE decision depends on them.
- BE planning loads the touched API specs, `Docs/ERD/ERD_v4.md`, backend conventions, and relevant infra docs before fixing scope or contracts.

### Build / investigate

- Read the touched `FE/app` files plus the matching FE source documents.
- Pull API docs only for the client contract actually being consumed.
- If the task is documentation-only, still verify the current behavior against the representative navigation files before changing shared harness text.
- BE build or investigation reads touched `BE/` files plus matching API, ERD, backend convention, and infra docs before changing shared harness text.

### Review / QA / ship

- Re-read the same FE source documents used for build or planning.
- Validate both axes separately:
  - route / state correctness against current code
  - visual / naming / accessibility contract against 1차 FE docs
- Re-read the same BE source documents used for build or planning when the task touches backend contracts or shared data assumptions.
- Validate BE changes separately against:
  - actual `BE/` implementation facts
  - `Docs/API/` contract
  - `Docs/ERD/ERD_v4.md` and backend conventions where relevant

## Priority rule for FE claims

1. Confirm current implementation facts in actual `FE/app` code.
2. Confirm target contract in the 1차 FE documents.
3. Use Figma handoff and `FE/mockup/` to resolve missing or visual details.
4. Use `Docs/PRD/`, `Docs/기획/`, and only the needed `Docs/API/` as supporting context.
5. Use 2차 정리 문서 only as index or historical explanation.
6. Use `.ai/PROJECT.md` only as a shared summary.

## Priority rule for BE claims

1. Confirm current implementation facts in actual `BE/` code.
2. Confirm contract in `Docs/API/`.
3. Confirm shared model assumptions in `Docs/ERD/ERD_v4.md`.
4. Use backend conventions and infra docs for implementation and runtime rules.
5. Use `Docs/PRD/` and `Docs/기획/` as product context only.
6. Use `.ai/PROJECT.md` only as a shared summary.
