# FE 대중교통 길찾기 `transit` API 호출 디버그 리포트
> 작성일: 2026-05-11  
> Work Lane: FE  
> 이슈: 사용자가 대중교통 길찾기 화면으로 이동하긴 하지만 실제로 `POST /routes/search/transit`가 정상 호출되는지 불명확하고, 현재 앱에서는 대중교통 경로가 동작하지 않는 것으로 체감되는 문제

## 1. 증상 요약

- 사용자는 경로 설정 화면에서 대중교통 탭까지는 진입한다.
- 하지만 사용자 체감은 "대중교통 길찾기가 안 된다"에 가깝다.
- FE 코드 기준으로는 `transit` 호출 경로가 존재하지만, 실제 런타임에서
  - 호출 직전 인증 게이트에서 막히는지
  - 실제 HTTP 전송은 나가는데 서버/네트워크에서 실패하는지
  - 타임아웃으로 끊기는지
  - 응답은 오지만 FE가 다른 상태로 덮어쓰는지
  를 이 세션 안에서 최종 증명하지는 못했다.

## 2. 현재 구현 기준 확인 사실

### 2.1 경로 설정 진입 시 호출 흐름

- `RouteSettingViewModel`은 경로 설정 진입 시 먼저 도보 검색을 수행한다.
  - 파일: `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingViewModel.kt`
  - 기준 지점: `loadRouteShell()` 내부 `walkSearchData` 로드
- 이후 `determineDefaultTravelMode()`에서 GraphHopper `SAFE` 도보 거리 `750m` 기준으로 기본 탭을 결정한다.
  - `SAFE distance > 750m` 이면 기본 탭을 `TRANSIT`으로 잡고 `fetchSearchData(mode = TRANSIT)`를 추가로 호출한다.

### 2.2 사용자가 대중교통 탭을 직접 누를 때 호출 흐름

- 화면 탭 클릭은 `RouteSettingScreen`에서 `RouteSettingUiAction.TravelModeSelected(RouteTravelMode.TRANSIT)`로 올라간다.
- `RouteSettingViewModel.selectTravelMode()`는 `mode = TRANSIT`일 때 `fetchSearchData(mode = TRANSIT)`를 직접 호출한다.
- 즉, 수동 탭 전환 경로에서도 FE 코드상 `transit` 검색 호출은 살아 있다.

### 2.3 실제 엔드포인트 경로

- `RouteRepository.getTransitRouteSearchData()`는 `remoteDataSource.searchTransitRoutes(request)`로 내려간다.
- `RouteRemoteDataSource.searchTransitRoutes()`는 아래 path로 POST 한다.
  - `"/routes/search/transit"`
- `RouteRemoteDataSourceTest`에도 이 path를 검증하는 단위 테스트가 존재한다.

### 2.4 현재 debug 환경 설정

- `FE/app.local.properties`
  - `app.debug.baseUrl=https://api.dev.busaneumgil.com/`
  - `app.debug.mockMode=false`
- 즉 현재 FE debug 설정은 mock 모드가 아니라 dev 서버 실호출 기준이다.

## 3. 실제 호출 전에 막힐 수 있는 지점

### 3.1 인증 세션 게이트

- 경로 검색도 `DefaultRouteRepository.getSearchData()` 안에서 `runAuthenticatedRemoteRequest()`를 탄다.
- `AuthenticatedRequestRunner`는 `authSessionRepository.getAuthGateState().authSession`이 없으면 바로 `MissingSession`을 반환한다.
- 이 경우 실제 `RouteRemoteDataSource.searchTransitRoutes()`까지 내려가지 못하고, HTTP 요청도 전송되지 않는다.

### 3.2 짧은 route timeout 설정

- `AppContainer`에서 `RouteRemoteDataSource` 생성 시 timeout을 별도로 주고 있다.
  - `connectTimeoutMillis = 5_000`
  - `readTimeoutMillis = 7_000`
- dev 서버 응답이 느리면 FE는 `CLIENT_TIMEOUT`으로 정규화해서 실패 처리한다.

### 3.3 네트워크 실패 정규화

- `RouteRemoteDataSource`는 아래 실패를 `RouteApiException`으로 정규화한다.
  - `SocketTimeoutException` -> `ROUTE_CLIENT_TIMEOUT`
  - `UnknownHostException` -> `ROUTE_UNKNOWN_HOST`
  - `ConnectException` -> `ROUTE_CONNECTION_FAILED`
  - 기타 `IOException` -> `ROUTE_NETWORK_IO_ERROR`
- 따라서 런타임에서 "호출 안 됨"처럼 보여도 실제로는 호출 직전/직후 네트워크 실패일 수 있다.

## 4. 이번 세션에서 확인한 결론

### 4.1 확인된 것

- FE 코드상 `transit` API 호출 분기는 존재한다.
- 자동 기본 진입 분기와 수동 대중교통 탭 전환 분기 모두 `transit` 호출 코드가 있다.
- 실제 엔드포인트 path도 `POST /routes/search/transit`로 맞다.
- mock 모드는 꺼져 있다.

### 4.2 아직 확정하지 못한 것

- 실제 기기/에뮬레이터 런타임에서 `RouteRemoteDataSource` 로그가 찍히는지
- 세션이 없어서 저장소 레벨에서 막히는지
- dev 서버가 401/403/408/504/5xx를 돌려주는지
- 요청은 성공했는데 응답 파싱/화면 상태 반영에서 소실되는지

## 5. 이번 세션의 한계

### 5.1 런타임 로그 수집 불가

- 이 세션 환경에는 `adb`/`logcat` 접근이 없다.
- 따라서 실제 기기에서 `RouteRemoteDataSource`의
  - `routeRequest path=/routes/search/transit result=success ...`
  - `routeRequest path=/routes/search/transit result=failure ...`
  로그를 직접 확인하지 못했다.

### 5.2 테스트 러너 환경 불안정

- `:app:testDebugUnitTest`는 현재 변경과 무관하게 전역 `ClassNotFoundException`으로 실패한다.
- 다만 `:app:compileDebugKotlin`, `:app:compileDebugUnitTestKotlin` 컴파일 단계는 통과했다.
- 따라서 현재 세션에서는 "코드 경로 존재 여부"와 "빌드 가능 여부"까지만 신뢰할 수 있다.

## 6. 현재 판단

- FE 기준으로 "대중교통 화면까지는 가는데 API를 아예 안 부른다"라고 단정할 근거는 없다.
- 오히려 코드상으로는 `transit` 호출 자체는 맞게 배선되어 있다.
- 현재 가장 가능성 높은 원인은 아래 둘 중 하나다.
  1. 인증 세션 없음/만료로 저장소 레벨에서 HTTP 전송 전에 차단됨
  2. dev 서버 응답 지연 또는 네트워크/서버 오류로 `transit` 요청이 실패함

## 7. 다음 스레드에서 바로 확인할 우선순위

1. 실제 기기/에뮬레이터에서 `RouteRemoteDataSource` 로그를 수집한다.
2. 대중교통 탭 클릭 직후 `authSessionRepository.getAuthGateState()`가 유효한지 확인한다.
3. `routeRequest path=/routes/search/transit` 로그가 없다면 저장소 인증 게이트에서 끊기는지 확인한다.
4. 로그가 있다면 `failureKind`, `httpStatus`, `status`를 기준으로 FE/BE/infra 원인을 분기한다.
5. 필요하면 경로 설정 화면에 임시 디버그 상태 표면을 추가해 `walk 호출`, `transit 호출`, `auth missing`, `timeout`, `http failure`를 화면에서 바로 보이게 한다.

## 8. BE 확인 필요 항목

- `POST /routes/search/transit`가 현재 dev 환경에서 인증 필수인지 최종 계약 재확인 필요
- dev 서버에서 해당 API의 평균 응답 시간과 timeout/5xx 발생 여부 확인 필요
- FE에서 재현 시각 기준 server log 상 요청 수신 여부 확인 필요
- 401/403 발생 시 token reissue 흐름이 route search에도 정상 연결되는지 확인 필요

## 9. Cross-Lane Handoff

- FE에서 추가로 확인할 것
  - 실제 런타임 로그 수집
  - 필요 시 경로 설정 임시 디버그 표면 추가
- BE/infra에 전달할 것
  - `POST /routes/search/transit` dev 응답 시간
  - 요청 수신 여부와 응답 코드
  - 인증 요구 조건 및 reissue 연동 확인
