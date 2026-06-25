# FE KakaoMap 시설 마커 LabelLayer 탭 복구 기록

> 작성일: 2026-05-10
> Work Lane: FE
> 목적: 시설 마커를 Kakao SDK native `LabelLayer`로 복구한 뒤 발생한 탭 불가 이슈의 원인과 해결 방법을 기록한다.

## 1. 배경

- 시설 마커는 Compose projection overlay 방식에서 흔들림 문제가 있어 native `LabelLayer`로 되돌렸다.
- 마커 이미지는 vector drawable 직접 전달 대신, 런타임 `Bitmap` 생성 + 캐시 방식으로 교체했다.
- 마커 렌더링은 정상적으로 보였지만, 탭 시 `onLabelClick`이 발생하지 않고 지도 `terrain` 탭만 발생했다.

## 2. 증상

- 시설 마커를 눌러도 선택 이벤트가 발생하지 않았다.
- 로그에는 다음과 같은 패턴만 반복되었다.
  - `Map tap source=terrain ... suppressedByMarkerTap=false`
- `setOnLabelClickListener`, `setOnPoiClickListener` 어느 경로로도 시설 마커 탭 로그가 들어오지 않았다.

## 3. 확인한 내용

### 3.1 클릭 리스너 자체 문제는 아니었다

- `LabelLayer.setClickable(true)`, `LabelOptions.setClickable(true)`, `label.setClickable(true)`는 모두 적용되어 있었다.
- `setTag(markerId)` 기반 클릭 흐름도 정상으로 보였다.
- `setOnLabelClickListener`, `setOnPoiClickListener`, `setOnMapClickListener`를 모두 연결해도 시설 마커 탭이 전혀 잡히지 않았다.

### 3.2 Kakao SDK 문서 기준 zOrder 범위를 벗어나 있었다

- Kakao Label 가이드 기준 사용자 커스텀 `LabelLayer`의 권장 `zOrder` 사용 가능 범위는 다음과 같다.
  - `1000~1999`
  - `3000~3999`
  - `5000+`
- 반면 당시 시설 마커 layer는 `zOrder = 100`으로 생성하고 있었다.
- `0~999`는 지도 내부 POI 예약 범위라서, 렌더는 되더라도 클릭 히트테스트가 비정상일 수 있는 상태였다.

## 4. 최종 원인

- 시설 마커 커스텀 `LabelLayer`가 내부 예약 범위인 `zOrder = 100`에 올라가 있었다.
- 이 상태에서는 마커는 화면에 보이지만, 탭 대상 Label로 정상 분류되지 않았다.

## 5. 해결

- `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/map/component/KakaoMapViewport.kt`
  - 시설 마커 layer `zOrder`를 `100`에서 `1000`으로 변경했다.
  - 클릭 디버깅 과정에서 넣은 `poi`/`terrain` 추적 로직은 유지하되, 스팸성 디버그 로그는 정리했다.
- 기기 로그 기준 최종적으로 아래 로그가 확인되었다.
  - `Marker tap source=label markerId=3724 layerId=eumgil-map-markers duplicate=false`

## 6. 현재 결론

- 시설 마커는 Compose projection overlay가 아니라 Kakao native `LabelLayer`에 유지한다.
- 런타임 `Bitmap` 기반 마커 생성 방식은 정상 동작한다.
- 커스텀 tappable `LabelLayer`를 만들 때는 `zOrder`를 내부 예약 범위 밖으로 둬야 한다.

## 7. 재발 방지 메모

- Kakao custom marker가 "보이는데 탭만 안 되는" 경우 가장 먼저 다음을 확인한다.
  - layer `zOrder`가 사용자 허용 범위인지
  - layer / label `clickable`이 모두 true인지
  - `LabelLayer`를 과도하게 재생성하면서 tag/click 설정이 누락되지 않았는지
- 시설 마커와 같이 상호작용이 필요한 Label은 예약 범위 `0~999`를 피한다.

## 8. 검증

- 기기 실측 로그에서 시설 마커 탭이 `label` 경로로 수신되는 것을 확인했다.
- `:app:compileDebugKotlin` 통과
- `:app:compileDebugUnitTestKotlin` 통과
