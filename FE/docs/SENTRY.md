# FE Sentry 적용 계획

## 결정 요약

부산이음길 FE는 `FE/app` Android 앱에 Sentry를 적용한다. 목적은 전체 로그 수집이 아니라 crash, ANR, 핵심 기능 실패, API 계약 불일치처럼 개발자가 재현하고 수정해야 하는 오류를 추적하는 것이다.

초기 운영 원칙은 다음과 같다.

1. 지금 Sentry를 붙인다.
2. `development`/`qa` 환경에서 핵심 기능 실패를 적극적으로 수동 capture한다.
3. `production` 전송은 처음에는 끄거나 crash/ANR 중심으로 제한한다.
4. 출시 직전 quota와 노이즈를 보고 production 수동 capture 범위를 결정한다.
5. 비용 부담이 커지면 Firebase Crashlytics를 crash/ANR 기본 도구로 두고, Sentry는 고급 진단용으로 축소한다.

## Sentry의 역할

Sentry는 앱에서 발생한 오류 이벤트를 stack trace, release, device, OS, breadcrumb, tag와 함께 모아 어디서 왜 실패했는지 찾게 해주는 오류 추적 플랫폼이다.

일반 로그 저장소가 아니라, 개발자가 재현하고 수정해야 할 실패를 issue 단위로 묶어주는 진단 및 모니터링 도구로 사용한다.

## 프로젝트 안에서 존재하는 형태

Sentry는 `FE` 안에서 직접 컨테이너로 띄우는 서비스가 아니다. 기본적으로 Sentry SaaS 또는 별도 self-hosted Sentry 서버가 이벤트를 받고, `FE/app`에는 Android SDK, Gradle plugin, 초기화 코드, 수동 capture 호출만 존재한다.

- Sentry server: 이벤트를 저장하고 issue dashboard를 제공하는 외부 서비스 또는 self-hosted 서버
- Sentry Android SDK: 앱 런타임에 포함되는 라이브러리
- Sentry Gradle plugin: release build의 mapping/source context 업로드를 돕는 빌드 플러그인
- DSN: 앱이 어느 Sentry project로 이벤트를 보낼지 알려주는 공개 식별자
- Auth token: mapping/source 업로드용 CI 비밀값. 앱에 넣지 않는다.

따라서 "FE에 Sentry를 단다"는 것은 `FE/app` Gradle 설정, `BusanEumgilApp` 초기화, 공통 error reporter, 핵심 실패 지점의 `captureException` 호출을 추가한다는 뜻이다.

## Grafana와의 역할 분리

Sentry와 Grafana는 목적이 다르다.

| 도구 | 확인 대상 |
|---|---|
| Sentry | Android 앱 crash, ANR, Compose/ViewModel/state 오류, SDK/지도/위치/음성 오류, local storage 오류, API response contract mismatch, FE fallback 실패 |
| Grafana | API latency, 4xx/5xx 비율, 서버 exception, DB/infra 문제, endpoint별 장애, container 상태 |

앱에서 오류가 발생하면 Sentry dashboard의 Issues에서 먼저 확인한다. 서버 장애가 의심되면 같은 시간대 Grafana에서 API latency, HTTP error rate, container 상태를 확인한다.

백엔드 장애를 FE Sentry로 중복 감시하지 않는다. 단순 API timeout, 서버 500, 배포 중 일시 오류는 Grafana/BE 로그가 1차 source of truth다. FE Sentry는 "서버가 아픈가"가 아니라 "앱 사용자가 이 기능을 못 쓰게 된 원인이 FE 상태, 디바이스/SDK, local storage, FE/BE 계약 불일치, fallback 실패인가"를 확인하기 위해 사용한다.

## 환경별 적용 정책

### development

개발 단계는 원인 추적이 목적이므로 가장 넓게 수집한다. 단, 개인정보와 token은 절대 보내지 않는다.

수집 대상:

- crash
- ANR
- 핵심 기능의 caught exception
- mapper/parsing 실패
- API 계약 불일치
- SDK 초기화/콜백 실패
- 위치, 지도, 음성, local storage 실패

수동 capture 허용 feature:

- `auth_login`
- `route_search`
- `navigation`
- `report_submit`
- `map`
- `bookmark`
- `low_vision_voice`

### qa

QA 단계는 실제 사용자 흐름을 검증하는 환경이므로 development보다 좁고 production보다 넓게 수집한다.

수집 대상:

- crash
- ANR
- QA 시나리오를 막는 핵심 기능 실패
- 서버 응답 구조 불일치
- 재시도 후에도 복구되지 않는 API 실패
- 권한 허용 상태에서 위치/지도/음성 기능이 실패하는 경우

QA에서는 Sentry issue를 QA 리포트의 근거로 사용한다. issue에는 화면 id, feature, failure type, recoverable 여부가 tag로 남아야 한다.

### production

초기 production에서는 quota와 노이즈를 줄이기 위해 보수적으로 시작한다.

1차 production 수집:

- crash
- ANR
- 앱 시작 gate 실패
- 로그인/토큰 갱신 실패로 앱 진입 불가
- 길찾기/길안내/제보 제출 같은 핵심 플로우가 완전히 막힌 실패
- API 계약 불일치로 앱이 응답을 처리하지 못한 실패

초기 production에서 제외:

- 단순 네트워크 실패 1회
- 사용자 취소
- 권한 거부
- 검색 결과 없음
- 입력 validation 실패
- 정상적인 401 후 token refresh 성공
- Snackbar 안내 후 동일 화면에서 재시도 가능한 실패

출시 직전 Sentry issue 양, quota 사용량, 중복 이벤트를 보고 production 수동 capture 범위를 다시 정한다.

## 1차 수집 기준

Sentry에 보낸다:

- 앱이 죽는 uncaught exception
- Android ANR
- 앱은 죽지 않았지만 핵심 플로우가 막힌 caught exception
- 서버 응답 구조와 앱 mapper/domain model이 맞지 않는 데이터 계약 실패
- API 실패 이후 FE fallback/cache/outbox까지 실패해 사용자가 목적을 달성하지 못한 경우
- SDK, 권한, 위치, 지도, 음성처럼 디바이스 의존도가 높아 로컬 재현이 어려운 실패

Sentry에 보내지 않는다:

- 단순 화면 진입, 버튼 클릭, 정상 사용 로그
- 사용자가 취소한 소셜 로그인
- 권한 거부 같은 정상 사용자 선택
- 검색 결과 없음, 입력 validation 실패
- 정상적인 401 이후 token refresh 성공
- 네트워크 불안정 1회처럼 사용자가 재시도 가능하고 앱 상태가 보존되는 실패
- 서버 500, timeout, latency 증가 자체
- 백엔드에서 Grafana/로그로 확인 가능한 endpoint 장애 자체

판단 기준:

> 개발자가 나중에 stack trace와 상태 단서로 원인을 찾아야 하면 Sentry event다. 정상 사용자 선택이나 예상 가능한 UI 상태면 Sentry event가 아니다.

## API 오류 처리 원칙

API마다 Sentry를 다는 방식은 금지한다. `HttpJsonClient`나 repository 공통 계층에서 모든 API 실패를 자동으로 `captureException` 하는 것도 금지한다. 이 방식은 BE/Grafana가 이미 확인할 수 있는 장애를 FE Sentry에 중복 전송하고, quota와 노이즈를 빠르게 늘린다.

권장 구조:

1. 공통 API 계층에서는 실패를 분류만 한다.
2. ViewModel 또는 UseCase에서 "이 실패가 핵심 플로우를 막는가"를 판단한다.
3. 핵심 플로우가 막히거나 fallback까지 실패한 경우에만 `AppErrorReporter.capture(...)`를 호출한다.
4. mapper/parsing/API contract mismatch는 기능 중요도와 무관하게 `failure_type=api_contract`로 capture한다.

FE Sentry에 보낼 API 관련 오류:

- 서버 응답은 왔지만 FE 파싱이 실패한 경우
- `data` 누락, 필수 필드 누락, enum 불일치, 좌표 필드 누락, route geometry 파싱 실패
- API 실패 때문에 경로 검색, 길안내 시작, 제보 제출, 인증 gate 같은 핵심 플로우가 막힌 경우
- 서버 실패 이후 FE cache/outbox/local fallback까지 실패한 경우
- FE가 예상하지 못한 status/body 조합을 받아 UI 상태를 안전하게 만들 수 없는 경우

FE Sentry에 보내지 않을 API 관련 오류:

- 단순 네트워크 timeout 1회
- 서버 500 자체
- 백엔드 배포 중 일시 실패
- 검색 결과 없음
- validation 실패
- 401 이후 refresh 성공
- 사용자가 재시도하면 되는 일시 실패

결론적으로 FE Sentry는 backend observability를 대체하지 않는다. API 장애의 1차 관측은 Grafana/BE 로그가 담당하고, FE Sentry는 클라이언트 관점에서 사용자 플로우가 깨지는 지점과 FE/BE 계약 불일치를 담당한다.

## 기능별 capture 계획

| 기능 | capture 기준 | 주요 tag |
|---|---|---|
| 앱 시작 | `BusanEumgilApp` 초기화 중 crash, SDK 초기화 실패 | `feature=app_start`, `failure_type=sdk` |
| 인증 | 소셜 SDK 실패, 서비스 token 발급 실패, refresh 후 재시도 실패 | `feature=auth_login`, `failure_type=auth` |
| 지도 | Kakao Map 초기화/렌더/마커 데이터 변환 실패 | `feature=map`, `failure_type=sdk` |
| 장소 검색 | 응답 파싱 실패, 핵심 플로우를 막는 API 실패, fallback 실패 | `feature=place_search`, `failure_type=api_contract | api` |
| 경로 검색 | 경로 응답 파싱 실패, route option 생성 실패, 길안내 진입 불가 | `feature=route_search`, `failure_type=api_contract` |
| 길안내 | navigation session 시작 실패, 안내 상태 계산 실패, TTS 시작 실패 | `feature=navigation`, `failure_type=route_state` |
| 제보 | draft/outbox 저장 실패, 서버 제출 실패 후 outbox fallback 실패, 사진/위치 데이터 처리 실패 | `feature=report_submit`, `failure_type=local_storage | api` |
| 저장 경로/북마크 | 핵심 조회 실패 후 local mirror도 사용할 수 없는 경우, local mirror 손상 | `feature=bookmark`, `failure_type=api | local_storage` |
| 저시력자 음성 | STT/KWS/TTS 초기화 또는 처리 실패 | `feature=low_vision_voice`, `failure_type=voice` |

## tag 규칙

모든 수동 capture는 공통 tag를 가진다.

- `environment`: `development`, `qa`, `production`
- `feature`: 기능 단위
- `screen`: FE 화면 inventory의 화면 id
- `failure_type`: 실패 분류
- `recoverable`: 사용자가 앱을 계속 사용할 수 있었는지 여부
- `build_type`: `debug`, `release`
- `app_version`: versionName
- `version_code`: versionCode

권장 `failure_type`:

- `crash`
- `anr`
- `api`
- `api_contract`
- `auth`
- `local_storage`
- `sdk`
- `location`
- `permission`
- `voice`
- `route_state`

## 개인정보 및 보안 기준

절대 보내지 않는다:

- access token
- refresh token
- signup token
- social access token
- 정확한 현재 위치 좌표
- 상세 주소
- 음성 원본 또는 음성 인식 전문
- 사진 파일 또는 사진의 원본 경로
- 전화번호, 이름, 자유 입력 설명 전문

보내도 되는 값:

- 익명화된 user id 또는 내부 hash
- 사용자 유형: `LOW_VISION`, `MOBILITY_IMPAIRED`
- 화면 id
- feature id
- path parameter를 제거한 endpoint path
- HTTP status code
- 앱 version, build type, device model, Android version

## 구현 계획

### 1단계: 의존성 및 빌드 설정

목표:

- Sentry Android SDK 추가
- Sentry Gradle plugin 추가
- DSN/environment/release 설정을 build config 또는 manifest placeholder로 주입
- release build에서 mapping 업로드 준비

작업 위치:

- `FE/build.gradle.kts`
- `FE/app/build.gradle.kts`
- `FE/app.local.properties`
- CI secret 설정

설정 원칙:

- DSN은 앱에 포함될 수 있는 공개 식별자로 취급한다.
- `SENTRY_AUTH_TOKEN`은 mapping/source 업로드용 비밀값이므로 CI에만 둔다.
- production 수동 capture 활성화 여부는 build config flag로 분리한다.

### 2단계: 앱 초기화

목표:

- `BusanEumgilApp.onCreate()` 초반에 Sentry 초기화
- Kakao/Naver SDK 초기화보다 먼저 Sentry 초기화
- `beforeSend`에서 민감 정보 제거
- environment, release, dist, app version tag 설정

작업 위치:

- `FE/app/src/main/java/com/ssafy/e102/eumgil/app/BusanEumgilApp.kt`
- 신규 `core/observability` 또는 `app/observability` 패키지

### 3단계: 공통 error reporter

목표:

- ViewModel/Repository/mapper에서 Sentry API를 직접 흩뿌리지 않는다.
- 공통 래퍼로 tag 이름, 개인정보 필터링, 환경별 전송 정책을 통일한다.
- 공통 API 계층은 실패를 분류만 하고 자동 전송하지 않는다.
- ViewModel/UseCase가 사용자 플로우 영향도를 판단한 뒤 필요한 경우에만 전송한다.

권장 형태:

```kotlin
object AppErrorReporter {
    fun capture(
        throwable: Throwable,
        feature: String,
        screen: String? = null,
        failureType: String,
        recoverable: Boolean,
        extra: Map<String, String> = emptyMap(),
    ) {
        // Sentry.captureException(...) 호출.
        // token, 좌표, 주소, 사용자 입력 전문은 extra에 넣지 않는다.
    }
}
```

전송 정책:

- `development`: 핵심 기능 실패 수동 capture 허용
- `qa`: QA 시나리오를 막는 실패 수동 capture 허용
- `production`: 초기에는 crash/ANR 중심, caught exception은 allowlist만 허용
- API failure: 단순 서버 장애는 전송하지 않고, contract mismatch 또는 FE fallback 실패만 전송

금지 사항:

- `HttpJsonClient`에서 모든 HTTP 실패를 자동 `captureException` 처리
- repository 공통 runner에서 모든 exception을 자동 `captureException` 처리
- 모든 `catch`/`runCatching.onFailure`에 Sentry 호출 추가
- BE/Grafana에서 확인 가능한 서버 장애를 FE Sentry로 중복 전송

### 4단계: 핵심 기능 capture 추가

우선순위:

1. 인증 gate와 token refresh
2. 경로 검색/경로 응답 parsing
3. 길안내 시작/진행 상태
4. 제보 draft/outbox/server submit
5. 지도 SDK와 위치 기능
6. 저시력자 음성 기능
7. 북마크/저장 경로 조회

각 기능은 실패를 UI 상태로 처리하되, 개발자가 수정해야 하는 실패만 `AppErrorReporter.capture(...)`로 보낸다.

### 5단계: release 추적성

목표:

- Sentry issue에서 앱 버전과 build를 정확히 구분한다.
- minify/R8 적용 시 stack trace를 읽을 수 있게 mapping을 업로드한다.

필수 값:

- `release`: `com.ssafy.e102.eumgil@{versionName}+{versionCode}`
- `dist`: CI build number 또는 versionCode
- mapping upload: CI에서만 수행

### 6단계: QA 검증

QA 체크:

- 강제 crash 테스트 이벤트가 Sentry dashboard에 도착하는가
- crash issue가 release/environment별로 분리되는가
- 수동 capture 이벤트에 `feature`, `screen`, `failure_type`, `recoverable` tag가 붙는가
- token, 주소, 좌표, 사용자 입력 전문이 event에 포함되지 않는가
- 동일 실패가 과도하게 반복 전송되지 않는가

### 7단계: production 전환 판단

출시 전 확인:

- QA 기간 동안 월 error 예상량이 무료 quota 또는 예산을 넘는가
- 반복 event 상위 10개가 실제 수정 대상인가, 노이즈인가
- production에서 caught exception allowlist를 어디까지 열 것인가
- Firebase Crashlytics 병행이 필요한가

production 초기값:

- crash/ANR: on
- 핵심 플로우 caught exception: 제한적 on
- replay/log/tracing: off 또는 최소 샘플링

## quota 대응 전략

Sentry error quota는 issue 개수가 아니라 event 개수 기준으로 소모된다. 같은 crash가 하나의 issue로 묶여도 사용자 100명에게 1번씩 발생하면 100 errors로 계산된다.

대응 원칙:

- crash/ANR은 끄지 않는다.
- 수동 `captureException`은 feature allowlist로 제한한다.
- 정상 실패와 사용자 선택은 capture하지 않는다.
- 특정 수동 event가 quota를 태우면 해당 feature/failure type만 임시로 샘플링하거나 비활성화한다.
- production caught exception은 출시 직전까지 보수적으로 유지한다.

비용 부담이 커지는 경우:

- Firebase Crashlytics를 crash/ANR 기본 도구로 도입한다.
- Sentry는 API 계약 실패, 경로/길안내/제보 같은 고급 진단 이벤트 중심으로 축소한다.

## Sentry dashboard 확인 흐름

1. Android 앱에서 crash, ANR, 또는 수동 `captureException` 발생
2. SDK가 DSN을 통해 Sentry project로 event 전송
3. Sentry가 같은 원인의 event를 하나의 Issue로 grouping
4. 개발자가 Sentry Issues에서 stack trace, breadcrumb, tag, release 확인
5. 서버 장애가 의심되면 같은 시간대 Grafana에서 API latency/error rate 확인

## 참고

- Sentry Android Gradle plugin: https://docs.sentry.dev/platforms/android/configuration/gradle/
- Sentry Android ProGuard/R8 mapping: https://docs.sentry.dev/platforms/android/enhance-errors/proguard/
- Sentry ANR tracking: https://sentry.zendesk.com/hc/en-us/articles/25916393222171-How-can-I-track-ANR
- Firebase Crashlytics customization: https://firebase.google.com/docs/crashlytics/customize-crash-reports
