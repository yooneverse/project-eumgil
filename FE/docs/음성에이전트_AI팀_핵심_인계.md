# 음성에이전트 AI팀 핵심 인계

작성일: 2026-05-17
기준: 현재 working tree 코드 기준
범위: AI 담당자가 알아야 할 FE/BE/AI 음성 분석 계약과 후속 작업 경계

## 1. 코드 정합성 판단

원본 `FE/docs/음성에이전트 전역 전환 작업.md`의 큰 방향은 현재 코드와 대체로 맞다. 전역 음성 어시스턴트 계약, 바텀시트 UI, ViewModel, rule-based interpreter, AppNavHost 연결, 검색 화면 마이크 진입점 전환, `search/voice` 호환 route 유지 정책이 코드에 존재한다.

다만 원본 문서는 누적 작업 로그라 AI 담당자가 볼 정보가 과하고, 현재 코드 기준으로 아래 구분을 명확히 해야 한다.

- 전역 음성 어시스턴트는 현재 실제 AI 서버와 직접 연결되어 있지 않다.
- `VoiceAssistantViewModel`은 transcript를 받아 `RuleBasedVoiceAssistantInterpreter`로 `VoiceAssistantAction`을 만든다.
- 전역 어시스턴트의 현재 rule-based 인식 범위는 제보 명령과 부산역 검색 예시 수준이다.
- 기존 `/voice/analyze` 연동은 저시력 음성 입력과 legacy 검색 음성 흐름 쪽에 남아 있다.
- `SearchRoute.VoiceInput`은 삭제된 것이 아니라 호환용 route로 유지된다.
- `StopNavigation`, `ResumeNavigationGuidance`, `UnknownCommand`는 현재 AppNavHost navigation mapping이 없다.

## 2. 현재 FE 전역 어시스턴트 구조

핵심 파일:

```text
FE/app/src/main/java/com/ssafy/e102/eumgil/feature/voiceassistant/VoiceAssistantContract.kt
FE/app/src/main/java/com/ssafy/e102/eumgil/feature/voiceassistant/VoiceAssistantInterpreter.kt
FE/app/src/main/java/com/ssafy/e102/eumgil/feature/voiceassistant/VoiceAssistantViewModel.kt
FE/app/src/main/java/com/ssafy/e102/eumgil/feature/voiceassistant/VoiceAssistantOverlay.kt
FE/app/src/main/java/com/ssafy/e102/eumgil/app/navigation/AppNavHost.kt
FE/app/src/main/java/com/ssafy/e102/eumgil/app/navigation/MainNavGraph.kt
FE/app/src/main/java/com/ssafy/e102/eumgil/app/navigation/Route.kt
```

전역 action 모델:

```text
SearchPlace(query, editingTarget)
OpenReport
OpenSavedRoutes
OpenMyPage
OpenMap
StopNavigation
ResumeNavigationGuidance
UnknownCommand
```

현재 navigation mapping:

```text
OpenReport -> Report top-level
OpenSavedRoutes -> SavedRoute top-level
OpenMyPage -> MyPage top-level
OpenMap -> map home reentry
SearchPlace -> SearchRoute.Results.createRoute(query, editingTarget)
StopNavigation / ResumeNavigationGuidance / UnknownCommand -> mapping 없음
```

전역 overlay 소유권은 `AppNavHost`에 있다. feature 화면이 overlay 상태를 따로 만들지 않는다. 검색 화면의 마이크 버튼은 legacy `search/voice`로 이동하지 않고 전역 overlay open callback을 호출한다.

## 3. AI 서버와 BE 계약

FE 앱은 AI 서버를 직접 호출하지 않는다. 일반 흐름은 아래와 같다.

```text
FE Android
-> BE POST /voice/analyze
-> AI server POST /voice/analyze
-> BE ApiResponse(data)
-> FE
```

FE가 BE에 보내는 요청 body:

```json
{
  "text": "부산역 어디야",
  "mode": "MOBILITY_IMPAIRED",
  "history": []
}
```

BE가 AI 서버에 보내는 요청도 같은 형태다. `history` item은 아래 형식이다.

```json
{ "role": "user", "content": "부산역 어디야" }
```

AI 서버가 BE에 반환해야 하는 응답은 wrapper 없이 top-level JSON이어야 한다.

```json
{
  "intent": "PLACE_SEARCH",
  "placeName": "부산역",
  "confirmed": null,
  "confirmationMessage": null
}
```

BE가 FE에 반환할 때만 공통 응답 wrapper의 `data` 안에 들어간다.

```json
{
  "data": {
    "intent": "PLACE_SEARCH",
    "placeName": "부산역",
    "confirmed": null,
    "confirmationMessage": null
  }
}
```

허용 mode:

```text
MOBILITY_IMPAIRED
LOW_VISION
```

허용 intent:

```text
PLACE_SEARCH
UNKNOWN
```

중요한 BE 정책:

- `MOBILITY_IMPAIRED`에서는 BE가 AI 응답의 `confirmed`, `confirmationMessage`를 FE 응답에서 `null`로 만든다.
- `MOBILITY_IMPAIRED`에서는 BE가 AI로 넘기는 history를 비운다.
- `LOW_VISION`에서는 history, `confirmed`, `confirmationMessage`가 유지된다.
- `PLACE_SEARCH`인데 `placeName`이 비어 있으면 BE는 AI 호출 실패로 처리한다.

## 4. AI 코드 기준 주의점

현재 저장소에는 AI 서버 후보가 둘 있다.

```text
AI/app.py
AI/llm_test/server/app.py
```

주의:

- `AI/app.py`는 `/api/intent` placeholder만 제공한다. 현재 BE가 기대하는 `/voice/analyze`와 맞지 않는다.
- `AI/llm_test/server/app.py`는 `/voice/analyze`를 제공하고, BE 연동 계약과 더 가깝다.
- 배포 대상 AI 서버가 어느 파일인지 확인해야 한다. BE 설정 `external.ai.base-url`은 AI 서버의 `/voice/analyze`가 붙을 base URL을 기대한다.

AI 담당자가 production 연동을 맡는다면 우선 `AI/llm_test/server/app.py`의 `/voice/analyze` 계약을 기준으로 root AI app 또는 배포 entrypoint를 정리해야 한다.

## 5. 후속 작업 경계

현재 FE 전역 어시스턴트는 앱 내부 action 모델까지 준비되어 있지만, 실제 AI 서버가 이 action 전체를 반환하는 계약은 아직 없다.

따라서 후속 작업은 둘 중 하나로 결정해야 한다.

```text
안 A. AI 서버는 장소 분석만 담당한다.
-> AI 응답은 PLACE_SEARCH / UNKNOWN, placeName 중심으로 유지한다.
-> OpenReport, OpenMap 같은 앱 제어 명령은 FE rule-based interpreter가 처리한다.

안 B. AI 서버가 전역 앱 제어 명령까지 담당한다.
-> BE/FE/AI 계약을 확장해야 한다.
-> intent 또는 action 필드에 OpenReport, OpenSavedRoutes, OpenMyPage, OpenMap, StopNavigation 등을 표현할 방법을 새로 합의해야 한다.
-> FE의 VoiceAssistantAction과 BE 응답 DTO를 함께 바꿔야 한다.
```

현재 코드만 보면 안 A가 더 안전하다. 안 B로 가려면 BE API와 FE DTO 수정이 필요하므로 FE 단독 작업으로 처리하면 안 된다.

## 6. AI 담당자 체크리스트

- AI 서버 배포 entrypoint가 `/voice/analyze`를 제공하는지 확인한다.
- AI 응답은 BE 호출 기준 top-level JSON으로 반환한다. `data` wrapper를 붙이지 않는다.
- `intent`는 대문자 `PLACE_SEARCH` 또는 `UNKNOWN`으로 반환한다.
- `PLACE_SEARCH`이면 `placeName`을 반드시 비워두지 않는다.
- `MOBILITY_IMPAIRED`는 단발성 장소 분석으로 본다.
- `LOW_VISION`은 history 기반 멀티턴 확인 흐름으로 본다.
- `confirmed=null`은 아직 확인 전, `true`는 확인 완료, `false`는 부정 또는 재입력 요청으로 맞춘다.
- 전역 앱 제어 명령까지 AI가 담당할지 여부는 FE/BE와 계약 확정 후 진행한다.
- 현재 FE에서 `search/voice` route는 호환용이므로 AI 작업자가 제거 대상으로 보면 안 된다.

## 7. 검증 우선순위

AI/BE 계약 검증:

```text
POST /voice/analyze
body: {"text":"부산역 어디야","mode":"MOBILITY_IMPAIRED","history":[]}
expect: {"intent":"PLACE_SEARCH","placeName":"부산역","confirmed":null,"confirmationMessage":null}
```

저시력 멀티턴 검증:

```text
1차: {"text":"부산역 어디야","mode":"LOW_VISION","history":[]}
expect: confirmed=null, confirmationMessage 존재

2차: {"text":"응","mode":"LOW_VISION","history":[...이전 user/assistant...]}
expect: confirmed=true 또는 confirmed=false
```

FE 전역 어시스턴트 검증:

```text
./gradlew.bat :app:testDebugUnitTest --tests "com.ssafy.e102.eumgil.feature.voiceassistant.*"
./gradlew.bat :app:testDebugUnitTest --tests "com.ssafy.e102.eumgil.app.navigation.AppNavHostPolicyTest"
./gradlew.bat :app:testDebugUnitTest --tests "com.ssafy.e102.eumgil.app.navigation.MainNavGraphTopLevelNavigationPolicyTest"
```

한글 workspace 경로에서 Gradle worker classpath 문제가 나면 ASCII 경로 junction과 고정 Gradle/Android cache로 재검증한다.
