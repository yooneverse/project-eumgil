# FE 대중교통 길찾기 `transit` API 런타임 관측 보강 디버그 리포트

> 작성일: 2026-05-11  
> Work Lane: FE  
> 범위: FE 코드만 수정  
> 선행 문서: `2026-05-11_FE_대중교통_길찾기_transit_API_호출_디버그_리포트.md`

## 1. 이번 세션 결론

- FE 코드상 `POST /routes/search/transit` 호출 분기는 여전히 존재한다.
- 이번 세션의 실제 런타임 증거는 확보하지 못했다.
  - `C:\Users\SSAFY\AppData\Local\Android\Sdk\platform-tools\adb.exe devices` 결과: 연결 디바이스 없음
  - Android emulator AVD 목록도 비어 있었다.
- 대신 FE 내부에서 원인을 가리던 지점을 확인했고, 다음 런타임 1회로 바로 원인을 분리할 수 있도록 디버그 표면과 로그를 보강했다.

## 2. 확인된 실제 FE 원인

### 2.1 원인 1: 인증 세션 게이트 차단은 기존 remote 로그에 남지 않았다

- `DefaultRouteRepository.runAuthenticatedRemoteRequest()`는 remote datasource 호출 전에 인증 세션을 검사한다.
- 세션 없음 또는 재발급 실패 시 `RouteApiException(401)`을 직접 던진다.
- 이 경우 `RouteRemoteDataSource`까지 진입하지 않으므로 기존 `RouteRemoteDataSource` 로그만 보면 "`transit` 요청이 아예 안 나간 것"처럼 보일 수 있었다.

### 2.2 원인 2: ViewModel이 auth/http 실패를 일반 에러 문구로 뭉개고 있었다

- `RouteSettingViewModel.toRouteLoadErrorMessage()`는 timeout/network 외 대부분 실패를 일반 문구로 보여줬다.
- 따라서 `ROUTE_AUTH_MISSING_SESSION`, `ROUTE_AUTHENTICATION_FAILED`, `403`, `5xx`가 UI에서 서로 구분되지 않았다.

### 2.3 원인 3: remote 실패 로그에 `message`가 없어 서버 사유 분리가 어려웠다

- `RouteRemoteDataSource` 실패 로그에는 `failureKind/httpStatus/status`만 있고 `message`가 없었다.
- `EX5040`, `FR4030` 같은 상태 코드를 보더라도 사용자/개발자가 서버 메시지까지 한 번에 보기 어려웠다.

## 3. 이번 FE 수정

### 3.1 repository auth gate 로그 추가

- 파일: `FE/app/src/main/java/com/ssafy/e102/eumgil/data/repository/RouteRepository.kt`
- 인증 게이트에서 막히는 경우 아래 형식으로 로그가 남도록 추가했다.

```text
routeRequest path=/routes/search/transit result=failure layer=auth_gate httpStatus=401 status=ROUTE_AUTH_MISSING_SESSION
```

### 3.2 remote 실패 로그에 `layer`와 `message` 추가

- 파일: `FE/app/src/main/java/com/ssafy/e102/eumgil/data/remote/datasource/RouteRemoteDataSource.kt`
- 기존 remote 실패 로그를 아래처럼 보강했다.

```text
routeRequest path=/routes/search/transit result=failure durationMs=... layer=remote failureKind=HTTP_RESPONSE httpStatus=504 status=EX5040 message="..."
```

### 3.3 route setting 화면에 debug build 전용 관측 표면 추가

- 파일:
  - `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingContract.kt`
  - `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingViewModel.kt`
  - `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt`
- `RouteSettingUiState.loadDebugMessage`를 추가했다.
- debug build에서만 `Route debug info` 카드가 보인다.
- 성공/실패에 따라 아래와 비슷한 정보를 화면에서 바로 확인할 수 있다.

```text
mode=TRANSIT
path=/routes/search/transit
result=success
layer=REMOTE
source=SERVER_API
fromCache=false
searchId=...
```

```text
mode=TRANSIT
path=/routes/search/transit
result=failure
layer=AUTH_GATE
failureKind=HTTP_RESPONSE
httpStatus=401
status=ROUTE_AUTH_MISSING_SESSION
message=인증이 필요합니다.
```

### 3.4 auth 실패 사용자 문구 분리

- 인증 관련 401 상태는 일반 경로 오류가 아니라 로그인 필요 문구로 분리했다.
- 현재 문구: `로그인이 필요해요. 다시 로그인한 뒤 시도해 주세요.`

## 4. 다음 런타임에서 보는 법

### 4.1 화면

- debug build에서 경로 설정 화면 또는 경로 상세 화면 진입
- `Route debug info` 카드 확인

분기 기준:

- `result=success` + `layer=REMOTE` + `fromCache=false`
  - 실제 remote `transit` 요청이 나갔다.
- `result=failure` + `layer=AUTH_GATE`
  - FE repository 인증 세션 게이트에서 remote 호출 전에 막혔다.
- `result=failure` + `layer=REMOTE`
  - remote까지는 갔고, `failureKind/httpStatus/status/message`로 서버/네트워크 원인을 본다.
- `layer=CACHE`
  - 이번 액션은 새 HTTP가 아니라 캐시 결과를 사용했다.

### 4.2 logcat

디바이스 또는 에뮬레이터 연결 후:

```powershell
C:\Users\SSAFY\AppData\Local\Android\Sdk\platform-tools\adb.exe logcat -s RouteRepository RouteRemoteDataSource
```

핵심 로그:

- auth gate 차단
  - `RouteRepository`: `layer=auth_gate`
- remote 성공/실패
  - `RouteRemoteDataSource`: `result=success` 또는 `result=failure layer=remote`

## 5. 검증 결과

- `:app:compileDebugKotlin` 성공
- `:app:compileDebugUnitTestKotlin` 성공
- `:app:testDebugUnitTest --tests "com.ssafy.e102.eumgil.feature.route.RouteSettingViewModelTest"`
  - 기존 `initializationError / ClassNotFoundException` 때문에 실행 단계는 여전히 실패
  - 이번 변경과 직접 연결된 컴파일 오류는 해소됨

## 6. 아직 확정하지 못한 것

- 현재 dev 서버에서 실제 `POST /routes/search/transit`가 어떤 응답을 주는지
- 현재 사용자 세션이 실제로 비어 있는지, 재발급 실패인지, 서버 4xx/5xx인지
- 네트워크 timeout인지 서버 응답 지연인지

즉, 이번 세션의 실제 FE root cause는 "원인 분리 불가능한 관측 부족"이었고, 실제 서버/세션 원인은 다음 런타임 증거 수집이 필요하다.

## 7. BE handoff 초안

- dev 환경 `POST /routes/search/transit`의 실제 응답 시간과 4xx/5xx 발생 여부 확인 필요
- FE에서 다음 런타임에 `layer=REMOTE` 실패 로그를 수집하면, 그때의 `httpStatus/status/message` 기준으로 서버 원인 재확인 필요
- `401` 발생 시 route search에도 auth reissue가 정상 적용되는지 최종 확인 필요
