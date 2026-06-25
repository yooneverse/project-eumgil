# FE/BE Lane Contract

이 저장소는 단일 레포지만, AI 작업은 FE lane과 BE lane을 명확히 분리한다.

## Basic rules

- FE 작업은 `/fe-*` 스킬을 우선 사용한다.
- BE 작업은 `/be-*` 스킬을 우선 사용한다.
- FE와 BE가 함께 바뀌는 작업만 공통 스킬(`/plan`, `/start`, `/review` 등)을 사용한다.
- lane 전용 작업은 해당 lane의 코드와 문서를 1차 수정 범위로 삼는다.
- `docs-context.sh stale`로 제외된 문서는 해당 lane의 source of truth에서 잠시 제외한다.
- 반대 lane 수정이 필요하면 직접 섞지 말고 `## Cross-Lane Handoff`로 넘긴다.

## FE lane

FE lane의 기준은 `FE/docs` 전체가 아니라, 명시된 1차 FE 계약 문서와 실제 `FE/app` 코드다.

### Primary editable surfaces

- `FE/app/`
- `FE/docs/`
- `FE/mockup/`

### Primary FE documents

- `FE/docs/2026-04-22_부산이음길_FE_화면_인벤토리_및_라우트_맵.md`
- `FE/docs/2026-04-22_부산이음길_FE_디자인_컨벤션.md`
- `FE/docs/2026-04-24_부산이음길_FE_접근성_정보_라벨_가이드.md`
- `FE/docs/2026-04-13_부산이음길_FE_코드_컨벤션.md`
- `FE/docs/2026-04-13_부산이음길_FE_컴포넌트_가이드.md`
- `FE/docs/2026-04-21_S14P31E102-211_공통_UI_접근성_Figma_handoff.md`

### Representative code files for current behavior

- `FE/app/src/main/java/com/ssafy/e102/eumgil/app/navigation/Route.kt`
- `FE/app/src/main/java/com/ssafy/e102/eumgil/app/navigation/AppStartDestination.kt`
- `FE/app/src/main/java/com/ssafy/e102/eumgil/app/navigation/AppNavHost.kt`
- `FE/app/src/main/java/com/ssafy/e102/eumgil/app/navigation/MainNavGraph.kt`
- `FE/app/src/main/java/com/ssafy/e102/eumgil/app/navigation/OnboardingNavGraph.kt`
- `FE/app/src/main/java/com/ssafy/e102/eumgil/app/navigation/LowVisionNavGraph.kt`

### Supporting documents only

- `FE/docs/debug-report/2026-04-27_로그인_필수_전환_FE_정합성_및_구현_영향.md`
- `FE/docs/archive/2026-04-27_온보딩_디자인_화면_구성_분석.md`
- `FE/docs/debug-report/2026-04-27_FE_Docs_문서_정합성_재검토.md`
- `FE/docs/sprint_backlog/`
- `FE/docs/todo/`

### FE interpretation rules

- FE lane에서는 아래 두 축을 함께 읽는다.
  - `current implementation fact`: 실제 `FE/app` 코드
  - `target contract`: 1차 FE 문서와 직접 디자인 산출물
- 둘이 다를 때는 하나로 합쳐 단정하지 않는다.
- 특히 아래 항목은 구현 사실과 목표 계약을 분리해서 서술한다.
  - 로그인 진입과 `ProfileSetup`
  - `isProfileCompleted` 기반 start gate
  - `selectedPrimaryUserType`와 low-vision 분기
  - 공통 top-level route alias(`map`, `saved_route`, `report`, `my_page`)
  - 사용자 노출 탭 계약(`홈 / 북마크 / 제보 / 마이페이지`)
  - 저시력자 전용 `LV-*` 화면군과 별도 bottom tab 흐름
- `FE/mockup/`과 Figma handoff는 1차 FE 문서가 모호할 때 직접 근거로 쓴다.
- 2차 참고 문서는 1차 문서와 직접 산출물의 색인이나 실행 맥락일 뿐, 단독 source of truth가 아니다.
- 1차 FE 문서를 바꾸지 않은 채 2차 문서만 수정해서 기준을 바꾸지 않는다.

### Allowed BE touchpoints from FE lane

- `Docs/API/장소_도메인/`
- `Docs/API/사용자_도메인/`
- `Docs/API/길안내_도메인/`
- 필요 시 `Docs/API/음성_도메인/`
- `Docs/ERD/ERD_v4.md`

FE lane은 위 문서를 읽을 수 있지만 `BE/` 구현 파일은 수정하지 않는다. API 계약 자체가 틀렸거나 미정이면 `/be-plan` 또는 `/be-start`로 넘긴다.

### FE design contract

- `FE/docs/2026-04-22_부산이음길_FE_디자인_컨벤션.md`는 FE 시각 계약의 최상위 기준이다.
- `FE/docs/2026-04-22_부산이음길_FE_화면_인벤토리_및_라우트_맵.md`는 화면 구조와 route 목표 계약을 맡는다.
- `FE/docs/2026-04-13_부산이음길_FE_컴포넌트_가이드.md`는 공통 UI 조합 규칙을 맡는다.
- `FE/docs/2026-04-24_부산이음길_FE_접근성_정보_라벨_가이드.md`는 접근성 라벨과 읽기 규칙을 맡는다.
- 실제 `FE/app` 코드가 낡았더라도 디자인 정합성 작업에서는 코드를 목표 계약으로 승격하지 않는다.

## BE lane

BE lane의 1차 기준은 `Docs/API`, `Docs/ERD`, `Docs/skills/backend`, 실제 `BE` 코드다.

### Primary editable surfaces

- `BE/`
- backend 실행, 테스트, 설정, migration, infra-adjacent 파일

### Primary BE documents

- `Docs/API/`
- `Docs/ERD/ERD_v4.md`
- `Docs/skills/backend/`
- `Docs/컨벤션/2026-04-14_API_응답_코드_컨벤션.md`
- `Docs/인프라/`

### Allowed FE touchpoints from BE lane

- FE 화면 인벤토리와 route 맵 확인
- FE가 소비하는 API 흐름 확인
- 응답 shape 변경에 따른 FE 영향 기록
- FE 후속 요청은 `## Cross-Lane Handoff`로 기록

BE lane은 FE 구현 파일을 수정하지 않는다. 화면 상태나 ViewModel 수정이 필요하면 `/fe-plan` 또는 `/fe-start`로 넘긴다.

## Documentation placement

- FE 화면, 상태, 디자인, 접근성, 컴포넌트, FE 코드 규칙은 `FE/docs/`에 둔다.
- BE API, ERD, 응답/예외, backend 계층, 설정, 외부 연동, 인프라는 `Docs/API/`, `Docs/ERD/`, `Docs/skills/backend/`, `Docs/인프라/`에 둔다.
- 공통 제품 의도는 `Docs/PRD/`와 `Docs/기획/`에 둔다.
- shared harness 문서는 `.ai/`에 두되, lane별 source-of-truth 규칙과 shared team facts만 유지한다.

## Plan file requirements

lane 전용 계획은 `.ai/LOCAL/PLANS/current-sprint.md`에 아래 항목을 포함한다.

- `## Work Lane`
- `## Source Documents`
- `## Excluded Documents`
- `## Cross-Lane Handoff`
- `## 작업 체크리스트`
