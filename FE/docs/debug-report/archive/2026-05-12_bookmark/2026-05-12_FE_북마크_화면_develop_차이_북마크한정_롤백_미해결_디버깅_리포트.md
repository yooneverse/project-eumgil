# FE 북마크 화면 develop 차이 / 북마크 한정 롤백 미해결 디버깅 리포트
> 작성일: 2026-05-12
> Work Lane: FE
> 이슈: 북마크 화면이 `develop`과 다른 동작을 보이며, 북마크 경로만 한정한 네비게이션 롤백 이후에도 사용자 체감상 문제가 계속 남아 있는 상태

## 1. 증상 요약

- 사용자 피드백
  - "아직도 깨져잇는데ㅐ?"
  - "이거 또 안되는데?"
- 1차로 확인된 `FE/app/build.gradle.kts` 문법 오타는 제거했고, 앱 빌드는 다시 성공한다.
- 이후 `develop`과의 차이를 기준으로 북마크 경로만 한정한 롤백을 적용했지만, 사용자 기준 증상은 계속 남아 있다.
- 현재 시점에서는 "북마크 화면이 깨진다"는 현상은 확인됐지만, 구체적으로
  - 화면 레이아웃이 깨지는지
  - 장소/경로 탭 전환이 깨지는지
  - 북마크에서 홈/지도로 돌아가는 경로가 깨지는지
  - 데이터가 비어 보이는지
  중 어느 축인지 런타임 증거가 아직 분리되지 않았다.

## 2. 이번 조사에서 확인한 사실

### 2.1 `develop` 대비 북마크 관련 주요 차이

- `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/savedroute/SavedRouteScreen.kt`
  - `develop` 대비 접근성 semantics, no-ripple CTA, empty/error 상태 아이콘이 추가됐다.
  - 상위 레이아웃 뼈대(`Scaffold -> TopBar -> TabRow -> LazyColumn`)는 유지된다.
  - 즉 이 파일만 보면 "구조 자체가 완전히 바뀐 화면"은 아니다.

- `FE/app/src/main/java/com/ssafy/e102/eumgil/app/navigation/MainNavGraph.kt`
  - top-level 탭 정책이 `restoreState = false/saveState = false`에서 `true/true`로 바뀌었다.
  - `navigateToTopLevelMapForHomeEntry()`와 `MAP_HOME_REENTRY_RESET_KEY` 기반 reset 경로가 추가됐다.
  - 북마크 화면 내부의 "지도에서 장소 찾기" 복귀도 plain top-level 이동이 아니라 home reentry reset 경로를 타도록 바뀌었다.

- `FE/app/src/main/java/com/ssafy/e102/eumgil/app/navigation/AppNavHost.kt`
  - 홈 탭 선택 시 언제 home reentry reset을 탈지 결정하는 `shouldNavigateToTopLevelMapForHomeEntry()`가 추가됐다.
  - 이 로직은 북마크 화면에서 홈으로 나갈 때도 화면 상태 복원/초기화 결과를 바꿀 수 있다.

- `FE/app/src/main/java/com/ssafy/e102/eumgil/app/navigation/LowVisionNavGraph.kt`
  - 저시력 완료/복귀 경로가 `develop`과 달라졌다.
  - 일반 북마크 문제와 직접 동일하다고 단정할 수는 없지만, 저시력 북마크 흐름까지 포함하면 분기 수가 늘어난 상태다.

### 2.2 현재 워킹트리에 추가로 섞여 있는 로컬 변경

- 북마크 화면과 직접/간접으로 연결된 파일 중 현재 워킹트리 수정 상태인 파일
  - `AppNavHost.kt`
  - `MainNavGraph.kt`
  - `SavedRouteRoute.kt`
  - `SavedRouteViewModel.kt`
  - `LowVisionBookmarkRoute.kt`
  - `MapViewModel.kt`
  - `strings.xml`
- 따라서 지금 보이는 런타임 증상은 "브랜치가 `develop`과 다른 것"에 더해 "아직 커밋되지 않은 로컬 수정" 영향도 같이 받을 수 있다.

## 3. 2026-05-12에 실제로 적용한 북마크 한정 롤백

### 3.1 롤백 의도

- 사용자 요청: "`develop` 코드랑 뭐가 다른지 보고, 북마크만 한정해서 되돌려봐"
- 목표
  - 검색/제보/마이페이지/도착 완료 쪽 홈 복귀 정책은 건드리지 않는다.
  - 북마크 경로만 `develop`에 가깝게 되돌린다.

### 3.2 적용 내용

- `FE/app/src/main/java/com/ssafy/e102/eumgil/app/navigation/AppNavHost.kt`
  - `shouldNavigateToTopLevelMapForHomeEntry()`에서 `TopLevelRoute.SavedRoute.route`를 예외 처리했다.
  - 의미: 북마크 탭에서는 홈 탭 선택 시 home reentry reset을 강제하지 않는다.

- `FE/app/src/main/java/com/ssafy/e102/eumgil/app/navigation/MainNavGraph.kt`
  - `SavedRouteRoute`의 `onNavigateToMap`을 `navigateToTopLevelMapForHomeEntry()`에서 `navigateToTopLevel(TopLevelDestination.Map)`으로 되돌렸다.
  - 의미: 북마크 화면 내부 CTA의 지도 복귀도 plain top-level 이동으로 되돌린다.

- 테스트 기대값 동기화
  - `AppNavHostRoutingTest.kt`
  - `MainNavGraphTopLevelNavigationPolicyTest.kt`
  - 북마크 경로만 home reentry reset 대상에서 제외하도록 expectation을 바꿨다.

## 4. 검증 결과

### 4.1 컴파일 검증

- 성공
  - `./gradlew.bat :app:compileDebugUnitTestKotlin`
  - `./gradlew.bat :app:assembleDebug`

### 4.2 단위 테스트 실행 검증

- 아직 신뢰 불가
  - `:app:testDebugUnitTest` 계열은 기존부터 `initializationError`, `ClassNotFoundException`이 광범위하게 발생한다.
  - 이번 변경의 assertion 실패라기보다 테스트 런타임/클래스 로딩 축의 별도 이슈로 보인다.
- 따라서 현재 확보한 증거는
  - "수정 코드가 FE 빌드를 깨뜨리지는 않는다"
  - "테스트 소스도 컴파일된다"
  까지다.

### 4.3 사용자 검증 결과

- 사용자 기준 결과: "또 안된다"
- 결론
  - 북마크 한정 네비게이션 롤백만으로는 실제 증상을 해소하지 못했다.
  - 즉 현재 이슈의 root cause는 "북마크 -> 홈/지도 복귀 reset 정책" 하나로 닫히지 않는다.

## 5. 현재 중간 결론

### 5.1 배제된 가설

- `build.gradle.kts` 오타만이 문제였다: 아님
  - 빌드는 고쳤지만 사용자 증상은 계속된다.

- 북마크 경로의 home reentry reset 강제만이 문제였다: 아직 아님
  - 북마크 한정 롤백 후에도 사용자 증상이 남아 있다.

### 5.2 남아 있는 유력 가설

- 가설 A: 북마크 화면 자체(`SavedRouteScreen.kt`)의 시각/레이아웃 문제
  - `develop` 대비 접근성/상태 UI 보강이 들어가 있으므로, 특정 조건에서 실제 UI 배치가 달라졌을 수 있다.

- 가설 B: 북마크 데이터/상태 문제
  - `SavedRouteViewModel.kt`, `SavedRouteRoute.kt`, `LowVisionBookmarkRoute.kt`의 현재 로컬 수정이 섞여 있어
    장소/경로 탭 상태, empty/error/content 전이가 기대와 다를 수 있다.

- 가설 C: 북마크 -> 지도/홈 handoff 이후 map 쪽 상태 문제가 화면 깨짐으로 체감되는 경우
  - 사용자가 말한 "북마크 화면 깨짐"이 실제로는 북마크 화면을 떠난 뒤 map 상태/선택 상태 꼬임일 가능성도 남아 있다.

- 가설 D: 현재 워킹트리에 북마크 주변 파일 수정이 너무 많이 섞여 있어, `develop` 비교와 북마크 한정 롤백만으로는 충분히 고립되지 않은 상태

## 6. 다음 디버깅 계획

### 6.1 1단계: 증상 유형을 먼저 고정

- 목표
  - "깨짐"을 한 문장으로 고정한다.
- 확인 항목
  - 장소 탭이 안 보이는가
  - 경로 탭이 안 보이는가
  - 카드 레이아웃이 깨지는가
  - empty/error 상태가 잘못 뜨는가
  - 북마크에서 지도로 돌아간 뒤 상태가 꼬이는가
  - 저시력 북마크만 깨지는가 / 일반 북마크만 깨지는가
- 실행 방법
  - 실제 재현 순서를 1개로 고정한다.
  - 예: `앱 진입 -> 북마크 탭 -> 장소 탭 -> 첫 카드 터치` 또는 `북마크 탭 -> 지도에서 장소 찾기`

### 6.2 2단계: 워킹트리 오염 범위를 북마크 축으로만 압축

- 목표
  - 이번 이슈와 상관없는 수정 영향을 제거한다.
- 우선 비교할 파일
  - `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/savedroute/SavedRouteRoute.kt`
  - `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/savedroute/SavedRouteViewModel.kt`
  - `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/lowvision/LowVisionBookmarkRoute.kt`
  - `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/map/MapViewModel.kt`
  - `FE/app/src/main/res/values/strings.xml`
- 확인 기준
  - `develop -> HEAD` 차이
  - `HEAD -> working tree` 차이
  - 북마크 화면 이슈와 무관한 수정이 섞여 있는지

### 6.3 3단계: 북마크 화면 state를 직접 로그로 추적

- 목표
  - 사용자가 보는 순간 `SavedRouteUiState`가 무엇인지 확인한다.
- 로깅 포인트
  - `SavedRouteViewModel.observePlaceBookmarks()`
  - `SavedRouteViewModel.observeRouteBookmarks()`
  - `SavedRouteViewModel.onAction(...)`
  - `SavedRouteRoute`에서 collect하는 `SavedRouteUiEvent`
- 확인할 값
  - `selectedTab`
  - `placeContent.screenState`
  - `routeContent.screenState`
  - `places.size`, `routes.size`
  - `isEditMode`
  - `pendingPlaceRemovalIds`, `pendingRouteRemovalIds`

### 6.4 4단계: map handoff 경로 분리 검증

- 목표
  - 북마크 화면 문제와 map 복귀 문제를 분리한다.
- 확인 포인트
  - `SavedRouteUiEvent.NavigateToMap` 직전 상태
  - `MainNavGraph`의 `SavedRouteRoute.onNavigateToMap`
  - `AppNavHost.shouldNavigateToTopLevelMapForHomeEntry()`
  - `MapViewModel`의 선택 목적지 / recent destination / sheet visibility 상태
- 판단 기준
  - 북마크 화면에서 이미 UI가 깨져 있으면 savedroute 축 문제
  - 북마크 화면은 멀쩡하고 map 복귀 이후에만 깨지면 navigation/map 축 문제

### 6.5 5단계: 수정 우선순위

- 우선순위 1
  - 증상 분류 전까지는 추가 롤백을 더 하지 않는다.
- 우선순위 2
  - 재현 경로 1개를 고정한 뒤 그 경로의 state/log를 먼저 확보한다.
- 우선순위 3
  - 실제 원인이 `SavedRouteScreen` 구조가 아니라면, 화면 파일보다 `ViewModel/Route/Nav`를 먼저 수정한다.

## 7. 권장 실행 순서

1. 북마크 일반 모드/저시력 모드 중 어디서 깨지는지 분리
2. 재현 액션 1개 고정
3. `SavedRouteViewModel` state 로그 추가
4. `SavedRoute -> Map` handoff 로그 추가
5. 로그 기준으로 savedroute 축 / map 축 중 하나만 다시 수정
6. `compileDebugUnitTestKotlin`, `assembleDebug` 재검증

## 8. Cross-Lane Handoff

- 현재 기준 BE 작업 없음
- 지금까지 드러난 범위는 FE 화면, FE 라우팅, FE 상태, FE 리소스 축이다.
- API/DB/서버 응답 변경이 필요하다는 증거는 아직 없다.
