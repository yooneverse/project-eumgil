# AI Harness Guide

부산이음길 개발 중 AI가 `Docs/`와 `.ai/` 문서를 기준으로 계획, 구현, 검증을 이어가게 만드는 하네스입니다.

## 처음 한 번

```bash
./.ai/scripts/sync-adapters.sh
./.ai/scripts/verify.sh
./.ai/scripts/dashboard.sh
```

`bootstrap-template.sh`는 템플릿을 새 프로젝트에 처음 이식할 때만 쓰는 명령입니다. 이 레포에서 팀원이 반복 실행하지 않습니다.

Codex에서 `/plan`, `/start` 같은 스킬 명령이 보이지 않으면 `sync-adapters.sh` 실행 후 Codex 세션을 새로 시작합니다. Codex는 루트 `.agents/skills`를 발견 경로로 사용하며, 이 폴더는 `.ai/.agents/skills`를 가리키는 ignored shim입니다.

Claude에서 같은 스킬 명령이나 훅 설정이 보이지 않으면 `sync-adapters.sh` 실행 후 Claude 세션을 새로 시작합니다. Claude는 루트 `.claude/skills`와 `.claude/settings.json`을 사용하며, 이 경로는 `.ai/.claude`를 가리키는 ignored shim입니다.

## 개발 중 핵심 흐름

단독 FE 작업은 `/fe-*`, 단독 BE 작업은 `/be-*`를 사용합니다. FE와 BE가 함께 바뀌는 작업만 기존 공통 명령(`/plan`, `/start` 등)을 사용합니다.

문서가 아직 최신화되지 않아 이번 로컬 컨텍스트에서 기준으로 쓰면 안 되는 경우에는 아래처럼 한 번 표시합니다. 파일 내용이 수정되면 제외 상태는 자동으로 만료됩니다.

```bash
./.ai/scripts/docs-context.sh stale "Docs/기획/2026-04-09_화면명세서.md" "FE/docs 라우트 맵보다 오래됨"
./.ai/scripts/docs-context.sh status
./.ai/scripts/docs-source-report.sh
```

| 상황 | FE 명령 | BE 명령 | 목적 |
|---|---|---|---|
| 아이디어가 아직 흐릿함 | `/fe-make` | `/be-make` | 문제, 사용자, 범위, 하지 않을 일을 먼저 정리 |
| 구체적인 실행 가능한 계획 필요 | `/fe-plan` | `/be-plan` | 체크리스트, 성공 기준, 구현 순서, 검증 방법 작성 |
| 계획된 기능 구현 시작 | `/fe-start` | `/be-start` | `.ai/LOCAL/PLANS/current-sprint.md`의 계획 기준으로 구현 |
| 버그 수정 | `/fe-fix-bug` | `/be-fix-bug` | 재현, 원인 파악, 최소 수정, 회귀 테스트 |
| 원인부터 파악 필요 | `/fe-investigate` | `/be-investigate` | 바로 고치지 않고 증거와 가설을 정리 |
| 코드 리뷰 필요 | `/fe-review` | `/be-review` | 정확성, 유지보수성, 테스트 누락, 운영 리스크 점검 |
| 실제 흐름 검증 필요 | `/fe-qa` | `/be-qa` | 사용자 흐름 또는 API 흐름 기준 QA와 리스크 보고 |
| 보안 검토 필요 | `/fe-security-review` | `/be-security-review` | 인증, 권한, 민감정보, 신뢰 경계 점검 |
| UI/API 경험 검토 필요 | `/fe-design-review` | `/be-design-review` | 화면 상태, 정보 구조, API 사용 경험 점검 |
| 현재 진행 상황 확인 | `/fe-dashboard` | `/be-dashboard` | 계획 체크리스트 기준 진행률과 다음 작업 확인 |
| 작업 회고와 개선 | `/fe-try` | `/be-try` | 무엇이 막혔고 다음에 무엇을 바꿀지 정리 |
| 반복 실수 기록 | `/fe-learn` | `/be-learn` | 반복 문제를 MEMORY/EVALS/SKILLS/ADR에 남김 |

개발 중에는 보통 아래 순서만 기억하면 됩니다.

```text
/fe-make  →  /fe-plan  →  /fe-start  →  /fe-review 또는 /fe-qa  →  /fe-try
/be-make  →  /be-plan  →  /be-start  →  /be-review 또는 /be-qa  →  /be-try
```

작은 작업은 `/fe-make` 또는 `/be-make`를 건너뛰고 바로 `/fe-plan` 또는 `/be-plan`으로 시작해도 됩니다.

## 백엔드 엔지니어링 하네스

BE 작업은 계획 단계에서 바로 구현하지 않고 아래 항목을 먼저 채웁니다. 기준 템플릿은 `.ai/PLANS/implementation-plan-template.md`입니다.

- Design Analyzer: 요구사항, 도메인, 예상 트래픽, 데이터 구조, 외부 의존성을 입력으로 두고 상태 흐름, API 후보, 데이터 변경 지점, 트랜잭션 경계, 실패 지점, 병목, 테스트 전략을 기록합니다.
- Failure Scenario Generator: 중복 요청, 캐시/DB 부분 성공, DB 커밋 후 응답 전 서버 종료, 처리 중 만료, 다중 인스턴스 동시 처리 시나리오를 먼저 씁니다.
- Implementation Harness: 수정 예상 파일, 수정 이유, 예상 부작용, 테스트 케이스, 롤백 조건을 작성한 뒤 영향 범위, 구현 순서, 테스트 계획, 코드 수정, 자체 리뷰 순서로 진행합니다.
- Verification Harness: 컴파일, 단위/통합 테스트, API 응답, DB 마이그레이션, 보안, 성능, 로그/메트릭을 검증합니다. Spring Boot는 가능한 경우 `./gradlew clean test`, `./gradlew check`, `./gradlew bootJar`를 사용합니다.
- Metrics Explainer: 평균 응답 시간, p95 응답 시간, 최대 TPS, 에러율, DB 쿼리 수, 외부 API 호출 수, 캐시 hit rate를 측정값 또는 명시적 blocker로 남깁니다.

## 스킬 명령어

모든 공통 스킬에는 FE/BE 전용 명령이 있습니다. 예: `/start`의 FE 버전은 `/fe-start`, BE 버전은 `/be-start`입니다.

| 공통 명령 | FE 명령 | BE 명령 | 역할 |
|---|---|---|---|
| `/make` | `/fe-make` | `/be-make` | 애매한 요청을 문제 정의, 대상 사용자, 범위, 비목표로 정리 |
| `/plan` | `/fe-plan` | `/be-plan` | 실행 가능한 계획, 체크리스트, 성공 기준, 검증 방법 생성 |
| `/plan-ceo-review` | `/fe-plan-ceo-review` | `/be-plan-ceo-review` | 제품 범위와 방향이 맞는지 검토 |
| `/plan-eng-review` | `/fe-plan-eng-review` | `/be-plan-eng-review` | 아키텍처, 데이터 흐름, 실패 모드, 테스트 전략 검토 |
| `/plan-design-review` | `/fe-plan-design-review` | `/be-plan-design-review` | UI/API 경험, 정보 구조, 접근성, 사용자 흐름 검토 |
| `/start` | `/fe-start` | `/be-start` | 승인된 계획을 기준으로 기능 구현 |
| `/fix-bug` | `/fe-fix-bug` | `/be-fix-bug` | 버그 재현, 원인 분석, 최소 수정, 회귀 테스트 추가 |
| `/investigate` | `/fe-investigate` | `/be-investigate` | 증거 수집과 가설 검증으로 원인 파악 |
| `/write-test` | `/fe-write-test` | `/be-write-test` | 부족한 테스트와 엣지 케이스 보강 |
| `/refactor-module` | `/fe-refactor-module` | `/be-refactor-module` | 동작을 유지하면서 모듈 구조와 경계 개선 |
| `/review` | `/fe-review` | `/be-review` | 코드 정확성, 유지보수성, 테스트 누락, 운영 리스크 리뷰 |
| `/security-review` | `/fe-security-review` | `/be-security-review` | 인증, 권한, 민감정보, 신뢰 경계, 악용 가능성 검토 |
| `/design-review` | `/fe-design-review` | `/be-design-review` | 구현된 UI/API 경험의 상태, 구조, 접근성 검토 |
| `/qa` | `/fe-qa` | `/be-qa` | 실제 사용자 흐름 또는 API 흐름 기준 검증과 버그/리스크 보고 |
| `/qa-only` | `/fe-qa-only` | `/be-qa-only` | 코드는 고치지 않고 QA 결과만 보고 |
| `/benchmark` | `/fe-benchmark` | `/be-benchmark` | 성능 기준선과 변경 전후 비교 기록 |
| `/ship` | `/fe-ship` | `/be-ship` | 배포 전 테스트, 문서, 리스크 최종 확인 |
| `/deploy-check` | `/fe-deploy-check` | `/be-deploy-check` | 배포 환경, 의존성, 롤백 가능성 확인 |
| `/canary` | `/fe-canary` | `/be-canary` | 배포 직후 핵심 기능과 장애 신호 확인 |
| `/document-release` | `/fe-document-release` | `/be-document-release` | 배포/운영 문서와 릴리스 기록 갱신 |
| `/try` | `/fe-try` | `/be-try` | 작업 또는 스프린트 회고와 개선점 정리 |
| `/learn` | `/fe-learn` | `/be-learn` | 반복 실수와 팀 규칙을 공유 지식으로 기록 |
| `/dashboard` | `/fe-dashboard` | `/be-dashboard` | 체크리스트 기준 진행 상황과 다음 작업 표시 |

## 주요 위치

| 경로 | 용도 |
|---|---|
| `.ai/PROJECT.md` | 부산이음길 프로젝트 공통 맥락 |
| `.ai/DOCS.md` | 기획, 계획, 구현, 검증 단계별로 읽어야 할 `Docs/` 기준 |
| `.ai/LANES.md` | FE/BE 단위별 코드, 문서, handoff 경계 |
| `.ai/ARCHITECTURE.md` | 하네스 구조와 공유/개인 상태 경계 |
| `.ai/WORKFLOW.md` | 팀 공통 개발 흐름 |
| `.ai/SKILLS/` | 스킬 원본 |
| `.ai/LOCAL/PLANS/` | 개인별 계획과 진행 상태, git ignore |
| `.ai/LOCAL/EVALS/` | 개인별 메트릭과 retry 로그, git ignore |
| `.ai/MEMORY/` | 반복되는 디버깅/운영 교훈 |
| `.ai/EVALS/` | 공유 품질 기준과 실패 패턴 |
| `.ai/DECISIONS/` | ADR 등 구조적 결정 |
| `.ai/RUNBOOKS/` | 로컬 실행, 릴리스, 롤백 절차 |
| `Docs/PRD/` | 제품 요구사항 |
| `Docs/API/` | API 명세 |
| `Docs/ERD/` | ERD와 데이터 모델 |
| `Docs/인프라/` | 인프라 설계와 운영 기준 |

## 운영 규칙

- 계획 없이 바로 구현하지 않는다.
- FE 작업은 `/fe-*`, BE 작업은 `/be-*`를 우선 사용한다.
- 작업 단계에 맞는 `Docs/` 문서를 `.ai/DOCS.md` 기준으로 먼저 확인한다.
- 최신화되지 않은 문서는 `docs-context.sh stale <문서경로> [이유]`로 로컬 제외하고, 제외 목록은 `docs-source-report.sh`에서 확인한다.
- lane 전용 작업은 `.ai/LANES.md` 기준으로 반대쪽 구현 파일을 직접 수정하지 않는다.
- 개인 작업 상태는 `.ai/LOCAL/`에 둔다.
- 팀 전체가 알아야 할 사실만 공유 `.ai/` 문서에 반영한다.
- 스킬이나 하네스 문서를 바꾸면 `sync-adapters.sh`와 `verify.sh`를 실행한다.
- 완료 판단은 구현 여부가 아니라 검증 결과를 기준으로 한다.
