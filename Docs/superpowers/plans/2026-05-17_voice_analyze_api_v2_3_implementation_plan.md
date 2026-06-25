# Voice Analyze API v2.3 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align backend `/voice/analyze` behavior and repo documentation with the 2026-05-17 v2.3 voice-analysis contract so both `MOBILITY_IMPAIRED` and `LOW_VISION` flows support multi-intent parsing, history forwarding, and route-aware context.

**Architecture:** Keep the current `PlaceVoiceAnalysisController -> PlaceVoiceAnalysisService -> AiVoiceAnalysisClient` path and expand the existing DTO and enum contract in place instead of renaming packages in this change. Treat the AI server as the source of intent inference, while BE remains responsible for request shaping, intent-specific payload validation, and response contract translation into the shared `ApiResponse`.

**Tech Stack:** Java 21, Spring Boot, Jackson, Gradle, MockMvc, Spring Test

---

## Source Documents

- External source spec: `/Users/ryuwon/Downloads/부산이음길_음성분석_API명세서_v2.3.docx`
- Repo API index: `Docs/API/2026-04-12_API_전체_목록.md`
- Repo voice API doc: `Docs/API/음성_도메인/2026-04-29_음성_분석_API_명세.md`
- Backend source-of-truth docs:
  - `Docs/skills/backend/backend-convention.md`
  - `Docs/skills/backend/api-response-error-convention.md`
  - `Docs/skills/backend/config-external-convention.md`
- Existing implementation:
  - `BE/src/main/java/com/ssafy/e102/domain/place/controller/PlaceVoiceAnalysisController.java`
  - `BE/src/main/java/com/ssafy/e102/domain/place/dto/request/VoiceAnalyzeRequest.java`
  - `BE/src/main/java/com/ssafy/e102/domain/place/dto/response/VoiceAnalyzeResponse.java`
  - `BE/src/main/java/com/ssafy/e102/domain/place/service/PlaceVoiceAnalysisService.java`
  - `BE/src/main/java/com/ssafy/e102/domain/place/type/VoiceIntent.java`
  - `BE/src/main/java/com/ssafy/e102/global/external/ai/AiVoiceAnalyzeCommand.java`
  - `BE/src/main/java/com/ssafy/e102/global/external/ai/AiVoiceAnalyzeResult.java`
- Existing test coverage:
  - `BE/src/test/java/com/ssafy/e102/domain/place/controller/PlaceVoiceAnalysisControllerTest.java`
  - `BE/src/test/java/com/ssafy/e102/domain/place/service/PlaceVoiceAnalysisServiceTest.java`
  - `BE/src/test/java/com/ssafy/e102/global/external/ai/RestTemplateAiVoiceAnalysisClientTest.java`
- Excluded stale docs: none

## Work Lane

- Lane: BE
- Scope boundary: backend docs, request/response DTOs, AI client command/result mapping, service validation, and automated tests for `/voice/analyze`
- Explicit non-goals: FE screen routing, STT/TTS UX, AI prompt design, AI server implementation, bookmark execution side effects
- Cross-lane dependency summary: FE must serialize assistant history with every nullable field present and must wire newly added intents after the BE contract lands

## Problem List

- Current implementation supports only `PLACE_SEARCH` and `UNKNOWN`.
- `MOBILITY_IMPAIRED` requests currently drop `history` before calling the AI server, which blocks the v2.3 multi-turn requirement.
- `currentRoute` is not accepted by `VoiceAnalyzeRequest` and is never forwarded downstream.
- `AiVoiceAnalyzeResult` and `VoiceAnalyzeResponse` are missing `category`, `bookmarkAction`, `departure`, `destination`, `reportType`, and `description`.
- `PlaceVoiceAnalysisService` hardcodes `confirmed` and `confirmationMessage` to `null` for `MOBILITY_IMPAIRED` and only validates `PLACE_SEARCH`.
- Controller/OpenAPI wording still describes "place extraction" rather than multi-intent voice analysis.
- The external spec contains internal inconsistencies:
  - Header says `v2.3`, but section 2 still says `v2.2`.
  - The changelog says the `model` parameter was removed, but one request example still includes `"model": "gemini"`.
- Working basis for this plan:
  - Trust the v2.3 header, change log, request/response field tables, intent list, and BE scope summary.
  - Do not re-add a `model` request field.
  - Keep classes under the existing `domain.place` package for this change to avoid unnecessary rename churn.

## Architecture And Data Flow

1. FE sends `text`, `mode`, optional `history`, and optional `currentRoute` to `POST /voice/analyze`.
2. `PlaceVoiceAnalysisController` validates shape and passes the request into `PlaceVoiceAnalysisService`.
3. `PlaceVoiceAnalysisService` builds `AiVoiceAnalyzeCommand` and forwards both modes' histories unchanged, plus `currentRoute` when provided.
4. `RestTemplateAiVoiceAnalysisClient` calls the external AI `/voice/analyze` endpoint and deserializes a richer `AiVoiceAnalyzeResult`.
5. `PlaceVoiceAnalysisService` validates intent-specific required fields before emitting `VoiceAnalyzeResponse`.
6. `ApiResponse.success(...)` wraps the response without changing status/message conventions.
7. Repo API docs are updated to match the BE contract and the known working assumptions from the user-provided spec.

## Backend Design Analyzer

### Inputs

- Feature requirements: v2.3 multi-intent voice analysis, multi-turn support for both modes, `currentRoute` forwarding, expanded response payload
- Domain description: read-only intent analysis endpoint; no DB persistence; AI server is the only external dependency in the request path
- Expected traffic: user-triggered voice-assistant requests; bursty but low write pressure
- Data structures: request DTO, history message DTO, AI command/result DTO, API response DTO, `VoiceIntent` enum
- External dependencies: AI voice analysis server behind `AiVoiceAnalysisClient`

### Outputs

- State flow: stateless request -> AI call -> intent validation -> response mapping
- API candidates: keep `POST /voice/analyze` unchanged and widen request/response payload
- Data mutation points: none inside BE; this is a pure orchestration/validation endpoint
- Transaction boundaries: none; no DB write transaction should be introduced
- Failure points: request validation, AI timeout/error, malformed AI payload, mismatched intent-to-field combinations
- Expected bottlenecks: AI latency and AI schema drift
- Test strategy: DTO mapping tests, service validation tests, controller contract tests, external client request-shape tests

### Required Questions

- Where is this feature's source of truth?
  - External v2.3 spec plus repo voice API doc after update; runtime intent inference itself remains AI-owned.
- What happens when duplicate requests arrive?
  - Safe to process repeatedly because the endpoint does not mutate BE state.
- Can a failed operation be retried safely?
  - Yes, BE-side retry is safe because no persistence occurs; user-visible duplicate AI cost is the only tradeoff.
- Where are the transaction boundaries?
  - No DB transaction boundary is required in this feature.
- Can concurrent requests break data consistency?
  - Not in BE state, but concurrent in-flight requests can receive different AI interpretations if history differs.

## Failure Scenario Generator

| Scenario | Expected behavior | Mitigation or test |
| --- | --- | --- |
| Duplicate request, rapid repeated click, or client retry | Each request is handled independently and returns a deterministic wrapper response | Service test asserting stateless handling; no server-side dedupe added |
| AI server returns HTTP 5xx or times out | BE returns `V5020` voice-analysis failure without leaking internal endpoint details | `RestTemplateAiVoiceAnalysisClientTest` for server error mapping |
| AI server returns `intent=PLACE_SEARCH` with blank `placeName` | BE rejects the payload as invalid AI output | Service validation test |
| AI server returns `intent=REPORT` without `reportType` | BE rejects the payload as invalid AI output | New service validation test |
| `MOBILITY_IMPAIRED` request includes history/currentRoute but BE strips it | This must not happen after v2.3; payload is forwarded unchanged | Client-command mapping test plus service argument-capture test |
| FE omits `currentRoute` for mobility overlay use cases | Request remains valid; AI receives `null` and loses route context only for that call | Controller/service tests documenting optional behavior |
| FE serializes assistant history without nullable fields | AI may lose conversational context; this is a FE contract failure, not a BE transform | Record in `Cross-Lane Handoff`; do not "fix" by inventing BE defaults |
| AI returns a new intent not yet modeled in `VoiceIntent` | Deserialization or enum mapping fails fast and surfaces as AI failure until BE is updated | Enum-constrained contract test and doc update process |

## Execution Units

### Task 1: Lock the repo documentation to the v2.3 contract

**Files:**
- Modify: `Docs/API/음성_도메인/2026-04-29_음성_분석_API_명세.md`
- Modify: `Docs/API/2026-04-12_API_전체_목록.md`

- [ ] **Step 1: Update the voice API doc metadata and change summary**
  - Reflect `최종 수정일: 2026-05-17`.
  - Replace the old place-only scope with the v2.3 intent matrix, `currentRoute`, and the BE policy change for mobility history forwarding.

- [ ] **Step 2: Resolve spec inconsistencies in the repo doc**
  - Explicitly note that the repo implementation follows the v2.3 field tables and BE summary.
  - Remove any mention of a client-supplied `model` parameter from the repo doc.

- [ ] **Step 3: Update the API index entry**
  - Change the voice-analysis summary from "place extraction" to "multi-intent analysis with history/currentRoute context".

- [ ] **Step 4: Review the docs diff against the external spec**
  - Confirm the repo doc mirrors the external v2.3 contract and that the index points to the same behavior.

- [ ] **Step 5: Commit after user approval**
  - Keep docs changes in the same commit as BE contract changes unless the user wants a docs-only split.

### Task 2: Expand the request contract and AI command mapping

**Files:**
- Modify: `BE/src/main/java/com/ssafy/e102/domain/place/dto/request/VoiceAnalyzeRequest.java`
- Modify: `BE/src/main/java/com/ssafy/e102/global/external/ai/AiVoiceAnalyzeCommand.java`
- Modify: `BE/src/test/java/com/ssafy/e102/domain/place/controller/PlaceVoiceAnalysisControllerTest.java`
- Modify: `BE/src/test/java/com/ssafy/e102/domain/place/service/PlaceVoiceAnalysisServiceTest.java`
- Modify: `BE/src/test/java/com/ssafy/e102/global/external/ai/RestTemplateAiVoiceAnalysisClientTest.java`

- [ ] **Step 1: Write the failing tests for `currentRoute` and mobility history forwarding**
  - Add controller coverage for accepting `currentRoute`.
  - Add service/client coverage proving `MOBILITY_IMPAIRED` history is no longer stripped.

- [ ] **Step 2: Run the targeted tests to confirm the current implementation fails**
  - Run: `./gradlew test --tests com.ssafy.e102.domain.place.controller.PlaceVoiceAnalysisControllerTest --tests com.ssafy.e102.domain.place.service.PlaceVoiceAnalysisServiceTest --tests com.ssafy.e102.global.external.ai.RestTemplateAiVoiceAnalysisClientTest`
  - Expected: failures around missing `currentRoute` support and empty mobility history.

- [ ] **Step 3: Implement the minimal request-contract changes**
  - Add `String currentRoute` to `VoiceAnalyzeRequest`.
  - Pass `history` through unchanged for both modes.
  - Add `currentRoute` to `AiVoiceAnalyzeCommand`.

- [ ] **Step 4: Re-run the targeted tests**
  - Run the same command as Step 2.
  - Expected: request-shape tests pass.

- [ ] **Step 5: Commit after user approval**
  - Candidate scope: request DTO, AI command, request-shape tests.

### Task 3: Expand the AI result and API response DTOs to v2.3

**Files:**
- Modify: `BE/src/main/java/com/ssafy/e102/domain/place/type/VoiceIntent.java`
- Modify: `BE/src/main/java/com/ssafy/e102/global/external/ai/AiVoiceAnalyzeResult.java`
- Modify: `BE/src/main/java/com/ssafy/e102/domain/place/dto/response/VoiceAnalyzeResponse.java`
- Create: `BE/src/test/java/com/ssafy/e102/domain/place/dto/response/VoiceAnalyzeResponseJsonTest.java`
- Modify: `BE/src/test/java/com/ssafy/e102/domain/place/controller/PlaceVoiceAnalysisControllerTest.java`

- [ ] **Step 1: Write the failing tests for the widened response contract**
  - Cover new intents: `CATEGORY_SEARCH`, `REPORT`, `NAVIGATE`, `OPEN_MY_PAGE`, `OPEN_MAP`, `ASK`, `NAVIGATION_END`, `SHOW_BOOKMARKS`, `SHOW_FAVORITE_ROUTES`, `LOGOUT`, `BOOKMARK_ADD`, `BOOKMARK_DELETE`.
  - Add a JSON serialization test that proves nullable response fields are emitted as `null` when the spec requires them.

- [ ] **Step 2: Run the DTO/controller tests to confirm the current contract is too narrow**
  - Run: `./gradlew test --tests com.ssafy.e102.domain.place.controller.PlaceVoiceAnalysisControllerTest --tests com.ssafy.e102.domain.place.dto.response.VoiceAnalyzeResponseJsonTest`
  - Expected: enum/field mismatches and missing properties.

- [ ] **Step 3: Implement the DTO widening**
  - Add the 12 new `VoiceIntent` values.
  - Add `category`, `bookmarkAction`, `departure`, `destination`, `reportType`, `description` to both AI result and API response.
  - Keep `confirmed` and `confirmationMessage` in the contract for both modes, with actual values governed by service logic.

- [ ] **Step 4: Re-run the DTO/controller tests**
  - Run the same command as Step 2.
  - Expected: response schema tests pass.

- [ ] **Step 5: Commit after user approval**
  - Candidate scope: enum + result/response DTOs + JSON contract tests.

### Task 4: Rework service validation and controller/OpenAPI wording

**Files:**
- Modify: `BE/src/main/java/com/ssafy/e102/domain/place/service/PlaceVoiceAnalysisService.java`
- Modify: `BE/src/main/java/com/ssafy/e102/domain/place/controller/PlaceVoiceAnalysisController.java`
- Modify: `BE/src/test/java/com/ssafy/e102/domain/place/service/PlaceVoiceAnalysisServiceTest.java`
- Modify: `BE/src/test/java/com/ssafy/e102/domain/place/controller/PlaceVoiceAnalysisControllerTest.java`

- [ ] **Step 1: Write failing service tests for intent-specific validation**
  - Required payload rules:
    - `PLACE_SEARCH`: non-blank `placeName`
    - `CATEGORY_SEARCH`: non-blank `category`
    - `BOOKMARK_ADD` / `BOOKMARK_DELETE`: non-blank `placeName` and `bookmarkAction`
    - `NAVIGATE`: non-blank `destination`; `departure` may be `null`
    - `REPORT`: non-null `reportType`; `description` optional
    - `ASK`: low-vision flow should preserve `confirmationMessage`; mobility flow may still return `null`
    - `UNKNOWN`, `OPEN_MAP`, `OPEN_MY_PAGE`, `SHOW_BOOKMARKS`, `SHOW_FAVORITE_ROUTES`, `LOGOUT`, `NAVIGATION_END`: no extra payload required

- [ ] **Step 2: Run the service tests to confirm the current logic is insufficient**
  - Run: `./gradlew test --tests com.ssafy.e102.domain.place.service.PlaceVoiceAnalysisServiceTest`
  - Expected: failures around stripped mobility fields and missing validation coverage.

- [ ] **Step 3: Implement the service and controller wording updates**
  - Remove mobility-only history stripping.
  - Stop force-nulling `confirmed` and `confirmationMessage` in BE except where the AI result or the spec genuinely requires `null`.
  - Add a focused validation helper keyed by `VoiceIntent`.
  - Update Swagger tag/operation text from place-only analysis to intent analysis.

- [ ] **Step 4: Re-run the focused tests**
  - Run: `./gradlew test --tests com.ssafy.e102.domain.place.service.PlaceVoiceAnalysisServiceTest --tests com.ssafy.e102.domain.place.controller.PlaceVoiceAnalysisControllerTest`
  - Expected: targeted service/controller tests pass.

- [ ] **Step 5: Commit after user approval**
  - Candidate scope: service logic, controller wording, validation tests.

### Task 5: Run BE verification and capture FE handoff

**Files:**
- Modify: `Docs/superpowers/plans/2026-05-17_voice_analyze_api_v2_3_implementation_plan.md`
- Optional modify: `Docs/API/음성_도메인/2026-04-29_음성_분석_API_명세.md` if validation reveals undocumented behavior

- [ ] **Step 1: Run the BE verification commands**
  - Run: `./gradlew clean test`
  - Run: `./gradlew bootJar`
  - Run: `./gradlew check`

- [ ] **Step 2: Record results and blockers**
  - If a command fails, capture the exact failing surface and do not mark BE ready.

- [ ] **Step 3: Record FE follow-up items**
  - Document assistant-history serialization requirements and the new intent handling expectations.

- [ ] **Step 4: Commit after user approval**
  - Candidate scope: final polish after verification only.

## Implementation Harness Preflight

1. Files expected to change
   - Docs:
     - `Docs/API/음성_도메인/2026-04-29_음성_분석_API_명세.md`
     - `Docs/API/2026-04-12_API_전체_목록.md`
   - BE main:
     - `BE/src/main/java/com/ssafy/e102/domain/place/controller/PlaceVoiceAnalysisController.java`
     - `BE/src/main/java/com/ssafy/e102/domain/place/dto/request/VoiceAnalyzeRequest.java`
     - `BE/src/main/java/com/ssafy/e102/domain/place/dto/response/VoiceAnalyzeResponse.java`
     - `BE/src/main/java/com/ssafy/e102/domain/place/service/PlaceVoiceAnalysisService.java`
     - `BE/src/main/java/com/ssafy/e102/domain/place/type/VoiceIntent.java`
     - `BE/src/main/java/com/ssafy/e102/global/external/ai/AiVoiceAnalyzeCommand.java`
     - `BE/src/main/java/com/ssafy/e102/global/external/ai/AiVoiceAnalyzeResult.java`
   - BE tests:
     - `BE/src/test/java/com/ssafy/e102/domain/place/controller/PlaceVoiceAnalysisControllerTest.java`
     - `BE/src/test/java/com/ssafy/e102/domain/place/dto/response/VoiceAnalyzeResponseJsonTest.java`
     - `BE/src/test/java/com/ssafy/e102/domain/place/service/PlaceVoiceAnalysisServiceTest.java`
     - `BE/src/test/java/com/ssafy/e102/global/external/ai/RestTemplateAiVoiceAnalysisClientTest.java`

2. Why each file changes
   - Request DTO + AI command: accept and forward `currentRoute` and both modes' history.
   - Enum/result/response DTOs: represent the widened v2.3 contract.
   - Service: enforce intent-specific invariants and remove obsolete mobility stripping.
   - Controller: fix OpenAPI wording and request examples if present.
   - Docs/tests: freeze the contract and prevent regression.

3. Expected side effects
   - FE consumers will receive richer `data` payloads and may need to handle more intents immediately.
   - AI server schema drift will surface faster because BE validation becomes stricter.
   - Null-field serialization may become explicit if a dedicated JSON test or annotation is added.

4. Cases that must be tested
   - `MOBILITY_IMPAIRED` multi-turn `REPORT`
   - `MOBILITY_IMPAIRED` `NAVIGATION_END` with `currentRoute`
   - `LOW_VISION` `PLACE_SEARCH` confirmation flow
   - `REPORT` with optional `description`
   - `NAVIGATE` with `departure = null`
   - `UNKNOWN` response with nullable payload fields

5. Rollback trigger or rollback path
   - Roll back if FE or AI integration proves incompatible with the widened response shape.
   - Keep rollback simple by reverting the voice-analysis change set as one unit; no DB migration is involved.

Implementation order:

1. Lock the repo doc and index wording.
2. Add failing tests for request-shape changes.
3. Widen request and AI command mapping.
4. Add failing tests for response schema and validation.
5. Widen enum/result/response DTOs.
6. Tighten service validation and controller wording.
7. Run targeted tests, then full BE verification.
8. Record FE handoff and any residual blockers.

## Cross-Lane Handoff

- FE -> BE requests:
  - None before implementation, assuming FE can already provide `history` and `currentRoute`.
- BE -> FE requests:
  - Update FE assistant-history serialization so the assistant JSON string includes all nullable fields, not just populated ones.
  - Wire new intents in FE flow handling: `CATEGORY_SEARCH`, `BOOKMARK_ADD`, `BOOKMARK_DELETE`, `NAVIGATE`, `SHOW_BOOKMARKS`, `SHOW_FAVORITE_ROUTES`, `LOGOUT`, `REPORT`, `NAVIGATION_END`, `OPEN_MY_PAGE`, `OPEN_MAP`, `ASK`.
  - Confirm whether FE expects explicit `null` fields in JSON or only semantic nulls after deserialization.
- Contract questions:
  - None blocking implementation; the stale `model` example is treated as an outdated example.
- Owner or next action:
  - BE implements this plan first, then hands the FE action list back with the verified response examples.

## Test And Validation Matrix

- Unit or module tests
  - `PlaceVoiceAnalysisServiceTest`
  - `VoiceAnalyzeResponseJsonTest`
- Contract tests
  - `PlaceVoiceAnalysisControllerTest`
  - `RestTemplateAiVoiceAnalysisClientTest`
- Integration or ETL checks
  - External AI request-shape verification through `MockRestServiceServer`
- Security checks
  - Request validation still enforced through `@Valid`
  - No new auth surface added
- QA scenarios
  - Mobility multi-turn report
  - Mobility navigation end with route context
  - Low-vision place confirmation positive and negative turns
- Benchmark or latency checks
  - Capture AI-call latency only as a blocker note unless a stable test harness exists
- Release gating checks
  - `./gradlew clean test`
  - `./gradlew bootJar`
  - `./gradlew check`

## Backend Verification Harness

| Check | Status | Command or evidence |
| --- | --- | --- |
| Compile success | Planned | `./gradlew clean test` |
| Unit tests | Planned | `./gradlew clean test` |
| Integration tests | Planned | MockRestServiceServer coverage in `RestTemplateAiVoiceAnalysisClientTest` |
| API response validation | Planned | `PlaceVoiceAnalysisControllerTest`, `VoiceAnalyzeResponseJsonTest` |
| DB migration validation | N/A | No schema change |
| Security validation | Planned | Existing auth + validation path only; no new permission model |
| Performance validation | Blocked: no stable benchmark harness yet | Record AI latency observations if available |
| Log and metric validation | Planned | Review warning/error logs only if AI failure mapping changes |
| Spring Boot package check | Planned | `./gradlew bootJar` |
| Full Gradle check | Planned | `./gradlew check` |

## Metrics Explainer

| Item | Measurement |
| --- | --- |
| Average response time | Blocked: not measured in planning stage |
| p95 response time | Blocked: not measured in planning stage |
| Maximum TPS | Blocked: not measured in planning stage |
| Error rate | Blocked: not measured in planning stage |
| DB query count | N/A |
| External API call count | 1 AI call per request |
| Cache hit rate | N/A |

## Risk Register

- Risk statement: The AI server may not yet return every new v2.3 field consistently.
  - Why it matters: stricter BE validation can convert silent drift into visible `V5020` failures.
  - What can be mitigated now: validate only fields that the spec marks as required for each intent and add focused tests for those rules.
  - What remains open: live AI contract stability must be verified outside unit tests.
  - Owner or next action: BE during implementation, then integration verification with AI owners if needed.

- Risk statement: FE may not be ready for the widened intent set and explicit assistant-history contract.
  - Why it matters: BE can ship correct responses that FE still ignores or mishandles.
  - What can be mitigated now: record concrete FE handoff items and verified example payloads.
  - What remains open: FE wiring and UX follow-through.
  - Owner or next action: FE after BE contract is verified.

- Risk statement: Null-field serialization may not match the spec examples without explicit tests.
  - Why it matters: AI follow-up history and FE parsing assumptions can drift if fields disappear when null.
  - What can be mitigated now: add a JSON serialization test and only introduce annotations/config if the test proves a mismatch.
  - What remains open: whether FE truly depends on transport-level explicit nulls versus parsed object semantics.
  - Owner or next action: BE during DTO test implementation, FE to confirm consumer expectation.

## Review Handoff

- Reviewers should inspect request-shape changes first, especially removal of mobility history stripping.
- Reviewers should verify the intent-specific validation table against the v2.3 spec, not against the old place-only behavior.
- Reviewers should confirm docs, DTOs, and tests all describe the same response schema.
- Reviewers should challenge any change that broadens behavior without a targeted regression test.

## QA Handoff

- Primary user flows:
  - Mobility report multi-turn
  - Mobility navigation end
  - Low-vision place confirmation positive/negative branches
- Degraded mode or failure scenarios:
  - AI 5xx
  - malformed AI payload for required fields
  - missing `currentRoute`
- Required fixtures or environments:
  - Local Gradle test environment
  - Mock AI server coverage via `MockRestServiceServer`
- Expected pass or fail signals:
  - Targeted tests pass before full `clean test`
  - `bootJar` and `check` pass before BE readiness is claimed

## Open Questions

- Should BE force transport-level explicit `null` serialization if FE confirms it depends on raw JSON shape rather than deserialized object semantics?

## Execution Notes

### 2026-05-17 verification snapshot

- Verified: targeted voice-analysis tests passed
  - `./gradlew test --tests com.ssafy.e102.domain.place.dto.request.VoiceAnalyzeRequestContractTest --tests com.ssafy.e102.domain.place.dto.response.VoiceAnalyzeResponseJsonTest --tests com.ssafy.e102.domain.place.controller.PlaceVoiceAnalysisControllerTest --tests com.ssafy.e102.domain.place.service.PlaceVoiceAnalysisServiceTest --tests com.ssafy.e102.global.external.ai.AiVoiceAnalyzeCommandContractTest --tests com.ssafy.e102.global.external.ai.RestTemplateAiVoiceAnalysisClientTest`
- Verified: `./gradlew bootJar` passed
- Verified blocker: `./gradlew clean test` failed outside the voice-analysis scope
  - Spring context tests requiring PostgreSQL connection failed with `CannotGetJdbcConnectionException`
  - `RouteServiceNamingConventionTest` failed in existing baseline
- Verified blocker: `./gradlew check` failed outside the voice-analysis scope
  - repository-wide `checkstyle` warnings already exist across many unrelated files
  - repository-wide `spotlessJavaCheck` failed in unrelated admin files already present on `develop`

### FE handoff

- FE must send `history` for both `LOW_VISION` and `MOBILITY_IMPAIRED`.
- FE may send `currentRoute` for mobility overlay calls to improve AI context.
- FE assistant history serialization must include all nullable response fields:
  - `intent`
  - `placeName`
  - `category`
  - `bookmarkAction`
  - `departure`
  - `destination`
  - `reportType`
  - `description`
  - `confirmed`
  - `confirmationMessage`
- FE intent handling must cover the expanded set:
  - `CATEGORY_SEARCH`
  - `BOOKMARK_ADD`
  - `BOOKMARK_DELETE`
  - `NAVIGATE`
  - `SHOW_BOOKMARKS`
  - `SHOW_FAVORITE_ROUTES`
  - `LOGOUT`
  - `REPORT`
  - `NAVIGATION_END`
  - `OPEN_MY_PAGE`
  - `OPEN_MAP`
  - `ASK`
