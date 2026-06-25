# FE KakaoMap 마커 흔들림 및 Label 실패 기록

> 작성일: 2026-05-09  
> Work Lane: FE  
> 목적: Kakao 지도 위 현재 위치/선택 핀 표시 실험 중 실패한 접근을 기록해 같은 시행착오를 반복하지 않도록 한다.

## 1. 문제 요약

- Kakao SDK 지도 위에 표시한 현재 위치/선택 핀 계열 마커가 지도 이동 중 안정적으로 고정되지 않았다.
- Compose 오버레이 방식에서는 이동 중 마커가 흔들리거나, 지도 이동을 따라왔다가 이동 종료 후 자기 위치로 돌아가는 현상이 있었다.
- Kakao SDK `LabelLayer`로 합치는 시도에서는 현재 위치 마커와 선택 핀이 보이지 않는 문제가 발생했다.

## 2. 당시 관련 상태

- 시설 마커는 `KakaoMapViewport.kt`의 `syncMarkers()`에서 Kakao SDK `LabelLayer`로 렌더링한다.
- 현재 위치/선택 목적지/선택 맵 핀은 `createKakaoProjectedMarkerRenderStates()` 기반으로 Compose 오버레이에 표시하는 구조였다.
- 현재 위치 아이콘은 `ic_map_current_location.xml` 리소스를 사용한다.
- 2026-05-09 기준 현재 위치 아이콘은 화살표에서 작은 원 + 파란 점 형태의 vector drawable로 변경했다.

## 3. 실패한 시도

### 3.1 Compose 오버레이 위치 계산 방식

- 구현 요지: `KakaoMap.toScreenPoint(LatLng)` 결과를 Compose `Image`의 `offset`에 적용했다.
- 증상: 지도 이동 중 화면 좌표가 실시간으로 맞지 않아 마커가 지도 좌표와 분리되어 보였다.
- 판단: 이동 종료 시점 좌표 재계산만으로는 지도와 같은 프레임에 붙어 있는 마커처럼 보이게 만들 수 없다.

### 3.2 이동 중 `postOnAnimation`으로 실시간 재계산

- 구현 요지: `setOnCameraMoveStartListener`에서 추적을 시작하고, `MapView.postOnAnimation` 루프로 매 프레임 `toScreenPoint()`를 다시 호출했다.
- 증상: 마커가 지도 이동을 따라가지만 흔들림이 생겼다.
- 원인 추정: Kakao SDK 지도 렌더링과 Compose 오버레이 렌더링이 같은 프레임 기준으로 동기화되지 않는다.
- 판단: Compose 오버레이를 지도 좌표에 완전히 고정하는 방식으로는 부적합하다.

### 3.3 현재 위치/선택 핀을 SDK Label로 이동

- 구현 요지: 현재 위치/선택 목적지/선택 맵 핀을 `createKakaoMarkerRenderStates()` 결과와 합쳐 기존 Label 렌더링 경로에 넣었다.
- 의도: 시설 마커와 동일하게 Kakao 지도 엔진 내부에서 렌더링해 흔들림을 제거한다.
- 증상: 현재 위치 마커와 선택 핀이 보이지 않았다.
- 롤백: 해당 Kotlin 로직 변경은 되돌렸고, 현재 위치 아이콘 변경만 유지했다.
- 원인 추정:
  - vector drawable이 Kakao `LabelStyle.from(context, drawableRes)`에서 기대한 bounds로 처리되지 않았을 수 있다.
  - `setApplyDpScale(true)`와 vector viewport/width/height 조합이 맞지 않았을 수 있다.
  - anchor 또는 rank/z-order가 SDK 기준과 맞지 않았을 수 있다.
  - 기존 시설 마커와 같은 레이어로 합칠 때 label id, rank, competition type 조합이 예상과 다르게 동작했을 수 있다.

## 4. 검증 기록

- `git diff --check`는 통과했다.
- 권한 있는 환경에서 `:app:compileDebugKotlin`은 통과했다.
- 권한 없는 환경에서는 Android SDK `package.xml` 접근 거부와 Google repository manifest 네트워크 접근 차단으로 Gradle 검증이 실패할 수 있다.

## 5. 현재 남긴 변경

- 유지: `FE/app/src/main/res/drawable/ic_map_current_location.xml`
- 내용: 현재 위치 마커를 화살표가 아니라 작은 원 + 파란 점 형태로 변경.
- 롤백됨: 현재 위치/선택 핀을 SDK Label로 합치는 Kotlin 로직.

## 6. 다음 디버깅 시 주의사항

- vector drawable을 Kakao `LabelStyle`에 바로 넣는 방식은 다시 반복하지 않는다.
- SDK Label 재시도는 PNG 리소스를 만든 뒤, 현재 위치 마커 하나만 단독으로 검증한다.
- 기존 시설 마커 레이어 전체 구조를 바꾸지 말고, 실험용 label id와 별도 layer 또는 최소 변경으로 확인한다.
- Compose 오버레이를 매 프레임 재계산하는 방식은 흔들림이 남으므로 해결책으로 보지 않는다.
- 선택 핀 UX가 허용되면 화면 중앙 고정 핀 방식이 가장 안정적이다.
