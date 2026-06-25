# 2026-05-19 FE → BE API 연동 작업 인계

## 목적

FE에서 목데이터가 노출되는 것처럼 보인 이슈를 확인한 결과, 현재 debug 빌드는 mock mode가 아니며 실제 API base URL을 바라보고 있다.
이 문서는 BE 개발자가 이어서 실제 API 응답/계약/서버 상태를 확인할 수 있도록 FE 호출 경로와 필요한 BE 확인 작업을 정리한다.

## FE 확인 결과

- `BuildConfig.IS_MOCK_MODE = false`
- `BuildConfig.IS_DEMO_MODE = false`
- `BuildConfig.BASE_URL = https://api.busaneumgil.com/`
- `DefaultRepositorySourcePolicy` 기준:
  - `PLACES`, `SEARCH`, `VOICE_ANALYZE`는 mock mode가 false이면 `REMOTE → LOCAL`만 사용한다.
  - mock mode가 true일 때만 `MOCK`을 강제 사용한다.
- `RouteRepository`는 별도 mock datasource를 주입받지 않고 remote API와 local cache만 사용한다.
- 따라서 현재 빌드에서 mock 데이터가 나온다면 우선순위는 다음 순서로 확인한다.
  1. 오래된 앱 설치본/리소스 캐시로 인한 화면 오표시
  2. remote 실패 후 local cache가 남아 있는 상태
  3. 저시력 안내 전용 fallback route 생성 로직
  4. 서버 API 응답 shape 불일치 또는 서버 미응답

## FE 검증 결과

아래 명령은 통과했다.

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "com.ssafy.e102.eumgil.core.ui.UserFacingCopyPolicyTest" --tests "com.ssafy.e102.eumgil.feature.route.RouteSettingViewModelTest" --tests "com.ssafy.e102.eumgil.feature.map.MapViewportConfigurationTest"
./gradlew.bat :app:compileDebugKotlin
```

단, 실제 단말에서 서버 API 응답까지 검증한 것은 아니므로 BE에서 API 로그와 응답 계약을 확인해야 한다.

## FE가 호출하는 주요 BE API

### 경로

FE 파일:

- `FE/app/src/main/java/com/ssafy/e102/eumgil/data/remote/datasource/RouteRemoteDataSource.kt`
- `FE/app/src/main/java/com/ssafy/e102/eumgil/data/repository/RouteRepository.kt`

호출 목록:

| 기능 | Method | Path | Body |
|---|---:|---|---|
| 도보 경로 탐색 | POST | `/routes/search/walk` | `{ "startPoint": { "lat": number, "lng": number }, "endPoint": { "lat": number, "lng": number } }` |
| 대중교통 경로 탐색 | POST | `/routes/search/transit` | `{ "startPoint": { "lat": number, "lng": number }, "endPoint": { "lat": number, "lng": number } }` |
| 경로 선택 | POST | `/routes/{routeId}/select` | `{ "searchId": string }` |
| 대중교통 도착 정보 갱신 | POST | `/routes/{routeId}/transit-refresh` | `{ "legSequence": number }` |
| 재탐색 | POST | `/routes/reroute` | `{ "routeId": string, "currentPoint": { "lat": number, "lng": number } }` |
| 경로 종료 | POST | `/routes/{routeId}/end` | empty body |
| 경로 평가 | POST | `/route-ratings` | `{ "sessionId": string, "score": number }` |

BE 확인 필요:

- 위 endpoint가 운영 base URL에서 모두 열려 있는지 확인한다.
- 인증이 필요한 endpoint는 `Authorization: Bearer {accessToken}` 헤더 기준으로 동작해야 한다.
- 401 응답 시 FE는 토큰 재발급을 시도하고, 실패하면 인증 필요 상태로 처리한다.
- `/routes/search/walk`, `/routes/search/transit` 응답은 FE mapper가 파싱 가능한 `RouteSearchResponseDto` shape를 유지해야 한다.
- 경로 geometry/polyline이 비어 있으면 FE는 지도 미리보기를 표시하지 못하고 요약 정보만 보여준다.

### 장소 검색

FE 파일:

- `FE/app/src/main/java/com/ssafy/e102/eumgil/data/remote/datasource/SearchRemoteDataSource.kt`
- `FE/app/src/main/java/com/ssafy/e102/eumgil/data/repository/SearchRepository.kt`

호출 목록:

| 기능 | Method | Path | Query/Body |
|---|---:|---|---|
| 장소 검색 | GET | `/places/search` | `keyword`, `size`, `sort`, optional `lat`, `lng`, `radius`, `cursor` |
| 음성 분석 | POST | `/voice/analyze` | `{ "text": string, "mode": string }` |

BE 확인 필요:

- `/places/search`가 `keyword`, 위치 기반 필터, cursor pagination을 현재 FE query 이름 그대로 받는지 확인한다.
- 빈 결과와 서버 오류를 구분해서 내려준다.
- 음성 분석의 `mode` enum 값은 FE의 `SearchVoiceMode.name` 그대로 전송된다.

### 지도 주변 장소/상세

FE 파일:

- `FE/app/src/main/java/com/ssafy/e102/eumgil/data/remote/datasource/PlacesRemoteDataSource.kt`
- `FE/app/src/main/java/com/ssafy/e102/eumgil/data/repository/PlacesRepository.kt`

호출 목록:

| 기능 | Method | Path | Query/Body |
|---|---:|---|---|
| 주변 장소 조회 | GET | `/places` | `lat`, `lng`, `radius`, optional `category`, `featureType` |
| 장소 상세 조회 | GET | `/places/{placeId}` | path variable |
| 지도 탭 장소 상세 | POST | `/places/detail` | `{ "lat": number, "lng": number, "clickType": string, "provider"?: string, "providerPlaceId"?: string, "nameHint"?: string }` |

BE 확인 필요:

- `/places`는 `lat`, `lng`, `radius`가 필수다.
- `category`와 `featureType`은 쉼표로 join된 문자열로 전달된다.
- `/places/{placeId}`는 없는 장소일 때 404를 반환하면 FE는 `null` 상세로 처리한다.
- `/places/detail`은 지도 탭 위치 기반 상세 조회에 사용된다.

## BE에서 우선 확인할 항목

1. 운영 API 서버 `https://api.busaneumgil.com/` 접근 가능 여부
2. 위 endpoint별 2xx/4xx/5xx 응답 로그
3. 경로 탐색 응답의 `searchId`, `routeId`, segment/leg/polyline/geometry 포함 여부
4. 장소 검색/주변 장소 응답 shape가 FE mapper와 맞는지
5. 인증 필요 endpoint에서 access token 만료/재발급 플로우가 정상인지
6. 404, RT4040 같은 no-route 케이스에서 FE가 구분할 수 있는 status/message를 유지하는지

## FE 쪽 참고 사항

- 현재 FE는 mock mode가 false일 때 places/search에서 mock fallback을 사용하지 않는다.
- remote 실패 후 local cache가 있으면 cached data가 보일 수 있다.
- 저시력 안내 쪽에는 서버 경로가 없는 경우 자체 fallback route를 생성하는 별도 로직이 있다.
  - 파일: `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/lowvision/LowVisionNavigationRequestBuilder.kt`
  - 이 로직은 mock mode와 별개이며, 서버 응답이 없거나 선택 경로가 없을 때 저시력 안내를 완전히 중단하지 않기 위한 fallback이다.

## 요청 사항

BE 개발자는 위 endpoint별 실제 응답을 확인하고, FE가 기대하는 request/response 계약과 다른 부분이 있으면 API 문서와 서버 구현을 맞춰야 한다.
FE에서 추가 수정이 필요한 응답 shape 변경이 있다면 endpoint, sample request, sample response, status code를 함께 전달해 달라.
