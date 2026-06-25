# 2026-05-07_부산이음길_FE_현재_폰트_현황_및_Pretendard_적용_가이드

최종 업데이트: `2026-05-13`

## 1. 문서 목적

- 이 문서는 부산이음길 FE의 현재 폰트 적용 상태를 코드 기준으로 다시 정리하고, 일반사용자 모드를 `Pretendard`로 전환할 때 필요한 기준을 문서화한다.
- 특히 아래 3가지를 이번 문서의 핵심 결정사항으로 다룬다.
  - 일반모드 Pretendard 굵기 매트릭스
  - 일반모드와 저시력모드 Typography 분리 방식
  - 일반모드에서 직접 사용 중인 `Black` / `ExtraBold` 정리 원칙
- 본 문서는 `current implementation fact`와 `Pretendard 적용 가이드`를 분리해 기록한다.

## 2. 확인 기준

### 2.1 코드 기준

- `FE/app/src/main/java/com/ssafy/e102/eumgil/app/MainActivity.kt`
- `FE/app/src/main/java/com/ssafy/e102/eumgil/core/designsystem/theme/Theme.kt`
- `FE/app/src/main/java/com/ssafy/e102/eumgil/core/designsystem/theme/Type.kt`
- `FE/app/src/main/java/com/ssafy/e102/eumgil/app/navigation/AppStartDestination.kt`
- `FE/app/src/main/java/com/ssafy/e102/eumgil/app/navigation/MainNavGraph.kt`
- `FE/app/src/main/java/com/ssafy/e102/eumgil/app/navigation/LowVisionNavGraph.kt`
- `FE/app/src/main/java/com/ssafy/e102/eumgil/app/navigation/OnboardingNavGraph.kt`
- `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/lowvision/LowVisionTypography.kt`
- `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/auth/**`
- `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/onboarding/**`
- `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/tutorial/**`
- `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/map/**`
- `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/search/**`
- `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/route/**`
- `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/navigation/**`
- `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/arrival/**`
- `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/savedroute/**`
- `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/report/**`
- `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/mypage/**`
- `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/terms/**`

### 2.2 문서 기준

- `FE/docs/2026-04-13_부산이음길_FE_디자인시스템_가이드.md`
- `FE/docs/2026-04-22_부산이음길_FE_디자인_컨벤션.md`
- `FE/docs/2026-05-04_부산이음길_FE_사용자모드_UI_개선_정리.md`

### 2.3 작업 기준

- 실제 현재 동작은 `FE/app` 코드를 우선으로 본다.
- 디자인 방향과 화면 일관성 기준은 FE 1차 문서를 `target contract`로 본다.
- 이 문서는 디버그 리포트 성격의 보조 문서이므로, 코드 사실과 적용 가이드를 빠르게 참조할 수 있게 유지한다.

## 3. 현재 폰트 시스템 현황

### 3.1 현재 앱 전체 기본 폰트

| 항목 | current implementation fact |
| --- | --- |
| 앱 전역 Theme | `MainActivity.kt`에서 전체 앱을 `BusanEumgilTheme`로 감싼다. |
| 전역 Typography | `Theme.kt`는 `MaterialTheme(typography = BusanEumgilTypography)`를 사용한다. |
| 일반모드 기본 FontFamily | `Type.kt`의 `BusanEumgilFontFamily`는 `koddi_ud_on_gothic_regular`, `koddi_ud_on_gothic_bold`, `koddi_ud_on_gothic_extra_bold`를 사용한다. |
| 저시력 전용 FontFamily | `LowVisionTypography.kt`의 `LowVisionFontFamily`도 동일한 온고딕 계열 리소스를 사용한다. |
| 현재 `res/font` 상태 | Pretendard 리소스는 아직 없고, 온고딕 3종만 존재한다. |

### 3.2 현재 Typography 정의 범위

`Type.kt`에서 명시적으로 커스텀한 role은 아래와 같다.

| role | 현재 weight | 비고 |
| --- | --- | --- |
| `displayLarge` | `SemiBold` | 커스텀 정의 |
| `headlineMedium` | `SemiBold` | 커스텀 정의 |
| `titleLarge` | `SemiBold` | 커스텀 정의 |
| `titleMedium` | `Medium` | 커스텀 정의 |
| `bodyLarge` | `Normal` | 커스텀 정의 |
| `bodyMedium` | `Normal` | 커스텀 정의 |
| `labelLarge` | `Medium` | 커스텀 정의 |

실제 화면 코드에서는 아래 role도 많이 사용한다.

- `headlineLarge`
- `headlineSmall`
- `titleSmall`
- `bodySmall`
- `labelMedium`
- `labelSmall`

즉, 일반모드 Pretendard 전환 시에는 `Type.kt`에서 위 role들까지 포함한 전체 Typography 기준을 먼저 명시해야 한다.

### 3.3 현재 라우트 분리 상태

| 항목 | current implementation fact | 해석 |
| --- | --- | --- |
| 일반모드 흐름 | `MainNavGraph.kt`, `OnboardingNavGraph.kt` 기반 | Pretendard 전환 대상 |
| 저시력모드 흐름 | `LowVisionNavGraph.kt` 기반 | 온고딕 유지 대상 |
| 저시력 온고딕 보호 방식 | 대부분 `LowVisionFontTheme` route wrapper로 보호 | 현재는 FontFamily만 별도 적용 |
| 예외 route | `LowVisionVoiceInputRoute.kt`, `TermsGuideRoute.kt` | Pretendard 전환 전 보호 정리 필요 |

## 4. 저시력모드 온고딕 유지 범위

### 4.1 현재 `LowVisionFontTheme` 적용 상태

| 상태 | 파일 | 비고 |
| --- | --- | --- |
| 적용 | `feature/lowvision/LowVisionHomeRoute.kt` | route wrapper 적용 |
| 적용 | `feature/lowvision/LowVisionSearchRoute.kt` | 동일 |
| 적용 | `feature/lowvision/LowVisionCategoryRoute.kt` | 동일 |
| 적용 | `feature/lowvision/LowVisionBookmarkRoute.kt` | 동일 |
| 적용 | `feature/lowvision/LowVisionRouteBriefingRoute.kt` | 동일 |
| 적용 | `feature/lowvision/LowVisionNavigationRoute.kt` | 동일 |
| 적용 | `feature/lowvision/LowVisionNavigationCompleteRoute.kt` | 동일 |
| 적용 | `feature/lowvision/LowVisionMyPageRoute.kt` | 동일 |
| 적용 | `feature/lowvision/LowVisionAppInfoRoute` | `LowVisionMyPageRoute.kt` 내부에서 보호 |
| 미적용 | `feature/lowvision/LowVisionVoiceInputRoute.kt` | 저시력 화면인데 wrapper 없음 |
| 미적용 | `feature/terms/TermsGuideRoute.kt` | 저시력 온보딩 흐름 성격인데 wrapper 없음 |

### 4.2 중요한 해석

- 현재는 앱 전체 기본 폰트도 온고딕이기 때문에, 위 예외 화면도 실제 렌더링은 온고딕으로 보인다.
- 하지만 일반모드 기본 폰트를 Pretendard로 바꾸면, wrapper가 없는 저시력 화면은 같이 Pretendard로 바뀔 수 있다.
- 따라서 `LowVisionVoiceInputRoute`, `TermsGuideRoute`는 Pretendard 전환 전에 반드시 저시력 보호 범위로 묶어야 한다.

## 5. 일반모드 Pretendard 굵기 매트릭스

이 섹션은 일반사용자 모드에서 사용할 Pretendard 기준안을 정의한다. 목표는 화면마다 임의로 `Black` / `ExtraBold`를 올리는 대신, 역할 기반의 일관된 weight 체계를 먼저 고정하는 것이다.

### 5.1 권장 weight 기준

| Pretendard weight | 용도 | 적용 role |
| --- | --- | --- |
| `400 Regular` | 본문, 설명, 보조 정보 | `bodyLarge`, `bodyMedium`, `bodySmall` |
| `500 Medium` | 메타 정보, 서브 라벨, 칩 | `labelSmall`, `labelMedium` |
| `600 SemiBold` | 카드 제목, 섹션 타이틀, 기본 CTA | `titleSmall`, `titleMedium`, `titleLarge`, `labelLarge` |
| `700 Bold` | 핵심 헤드라인, 강조 숫자, 주요 CTA | `headlineSmall`, `headlineMedium`, `headlineLarge`, `displayLarge` |
| `800 ExtraBold` | 일반모드 기본 토큰에서는 미사용 권장 | 예외적 검토 대상 |
| `900 Black` | 일반모드 기본 토큰에서는 미사용 권장 | 예외적 검토 대상 |

### 5.2 일반모드 Typography 목표안

| role | 권장 weight | 비고 |
| --- | --- | --- |
| `displayLarge` | `Bold(700)` | 온보딩/소개 최상위 카피 |
| `headlineLarge` | `Bold(700)` | 주요 수치 강조, 상단 핵심 문구 |
| `headlineMedium` | `Bold(700)` | 메인 헤드라인 |
| `headlineSmall` | `SemiBold(600)` 또는 `Bold(700)` | 화면 성격에 따라 선택, 기본은 `600` 권장 |
| `titleLarge` | `SemiBold(600)` | 섹션 타이틀 |
| `titleMedium` | `SemiBold(600)` | 카드 제목, 폼 타이틀 |
| `titleSmall` | `SemiBold(600)` | 리스트/요약 카드 제목 |
| `bodyLarge` | `Regular(400)` | 16sp 본문 |
| `bodyMedium` | `Regular(400)` | 14sp 본문 |
| `bodySmall` | `Regular(400)` | 보조 설명 |
| `labelLarge` | `SemiBold(600)` | 버튼, 배지, 주요 라벨 |
| `labelMedium` | `Medium(500)` | 보조 라벨 |
| `labelSmall` | `Medium(500)` | 캡션, 힌트, 상태 표기 |

### 5.3 적용 원칙

- 일반모드에서는 `800`, `900`을 Typography 기본값으로 채택하지 않는다.
- 강조가 필요해도 먼저 role 계층과 크기로 해결하고, 그 다음에 `600` 또는 `700` 안에서 조정한다.
- 숫자 강조, 핵심 CTA, 온보딩 헤드라인 정도만 `700`까지 허용하고, 일반 본문/카드 라벨은 `400~600` 범위로 정리한다.

## 6. 일반모드 / 저시력모드 Typography 분리안

현재 저시력 분리는 `LowVisionFontTheme`를 통해 `fontFamily`만 덮는 방식이다. 일반모드를 Pretendard로 바꾸려면 Typography 분리 단위를 한 단계 더 명확히 해야 한다.

### 6.1 현재 구조의 한계

| 항목 | current implementation fact | 한계 |
| --- | --- | --- |
| 일반모드 Theme | `BusanEumgilTheme` 1개 | 전역 Typography가 하나뿐이다. |
| 저시력 분리 방식 | `ProvideTextStyle(LocalTextStyle.current.merge(...))` | FontFamily는 덮지만, role별 크기/weight 정책은 전역 Typography 영향권에 남아 있다. |
| 저시력 예외 route | `LowVisionVoiceInputRoute`, `TermsGuideRoute` | wrapper 누락 시 일반모드 변경 영향 가능 |

### 6.2 권장 분리 방식

| 레이어 | 권장안 | 이유 |
| --- | --- | --- |
| 일반모드 | `PretendardTypography` + `PretendardFontFamily` | 일반사용자 전체 일관성 확보 |
| 저시력모드 | `LowVisionTypography` + `LowVisionFontFamily` | 저시력 화면만 온고딕 유지 |
| 적용 방식 | route wrapper 수준에서 저시력 전용 Theme 또는 Typography 적용 | 전역 Pretendard 변경과 분리 |

### 6.3 실무적으로 필요한 작업

1. `Type.kt`에 일반모드 Pretendard 기준 Typography를 완성한다.
2. 저시력 전용 role 세트를 별도로 정의한다.
3. `LowVisionVoiceInputRoute.kt`와 `TermsGuideRoute.kt`를 저시력 보호 범위에 편입한다.
4. 이후 일반모드 전역 Theme를 Pretendard로 바꾼다.

### 6.4 분리 후 기대 효과

- 일반모드는 Pretendard 기준으로 화면 전반의 무게감이 안정된다.
- 저시력 화면은 온고딕과 큰 weight 중심 규칙을 유지할 수 있다.
- 일반모드 Typography 조정이 저시력 화면에 연쇄 반영되는 위험을 줄일 수 있다.

## 7. 일반모드 `Black` / `ExtraBold` 직접 사용 정리안

현재 일반모드 화면 일부는 Typography role만으로 해결하지 않고 `FontWeight.ExtraBold`, `FontWeight.Black`, `FontWeight.Bold`를 직접 덮어쓰고 있다. Pretendard 전환 전후에 이 구간을 정리하지 않으면 화면별로 굵기 인상이 크게 달라질 수 있다.

### 7.1 직접 weight 사용이 확인된 일반모드 주요 구간

| 영역 | 대표 파일 | current implementation fact |
| --- | --- | --- |
| 로그인/소개 | `feature/auth/AuthScreen.kt` | `ExtraBold`, `Bold`, `SemiBold` 직접 사용 |
| 일반 온보딩 | `feature/onboarding/OnboardingScreen.kt` | `Black`, `SemiBold` 직접 사용 |
| 튜토리얼 | `feature/tutorial/TutorialScreen.kt` | `ExtraBold`, `Black`, `Bold`, `SemiBold`, `Medium` 직접 사용 |
| 경로 설정 | `feature/route/RouteSettingScreen.kt` | `ExtraBold`, `Bold`, `SemiBold`, `Medium` 직접 사용 |
| 일반 내비게이션 | `feature/navigation/NavigationScreen.kt` | `ExtraBold`, `Bold`, `SemiBold`, `Medium` 직접 사용 |
| 지도 오버레이 일부 | `feature/map/component/MapViewport.kt`, `MapViewportOverlayBackdrop.kt` | `ExtraBold` 직접 사용 |
| 마이페이지 일부 | `feature/mypage/**` | `Bold`, `SemiBold` 직접 사용 |

### 7.2 정리 원칙

| 현재 패턴 | Pretendard 전환 시 정리 원칙 |
| --- | --- |
| `Black(900)` 직접 사용 | 일반모드에서는 제거 우선. `Bold(700)` 또는 `SemiBold(600)`로 재조정 |
| `ExtraBold(800)` 직접 사용 | 일반모드에서는 제거 우선. `Bold(700)`로 치환 검토 |
| `Bold(700)` 직접 사용 | 헤드라인/주요 CTA 외에는 role 기반 스타일로 흡수 |
| `SemiBold(600)` 직접 사용 | 반복 사용 시 role에 반영하고 개별 override는 최소화 |
| `copy(fontSize = ...) + fontWeight override` | role 확장 또는 전용 token으로 수렴 검토 |

### 7.3 화면군별 정리 우선순위

| 우선순위 | 대상 | 이유 |
| --- | --- | --- |
| `P0` | `AuthScreen`, `OnboardingScreen`, `TutorialScreen`, `RouteSettingScreen`, `NavigationScreen` | 일반모드 Pretendard 인상에 직접 영향 |
| `P1` | `MapViewport`, `MapViewportOverlayBackdrop`, `MyPage*` | 화면별 강조 톤 불균형 가능성 |
| `P2` | 기타 일반모드 컴포넌트 | role 체계 확정 후 일괄 정리 가능 |

### 7.4 예외로 남길 수 있는 경우

- 브랜딩용 1회성 hero 카피
- 수치 카운트처럼 시각적 대비가 핵심인 아주 제한된 요소
- 단, 이 경우에도 기본 role에서 해결 가능한지 먼저 검토하고, 가능한 한 `700` 안에서 정리한다.

## 8. Pretendard 전환 시 권장 작업 순서

1. 저시력 보호 범위를 먼저 닫는다.
   - `LowVisionVoiceInputRoute`
   - `TermsGuideRoute`
2. 일반모드 Pretendard Typography role을 완성한다.
   - 누락 role 추가: `headlineLarge`, `headlineSmall`, `titleSmall`, `bodySmall`, `labelMedium`, `labelSmall`
3. 일반모드 직접 weight override를 `600/700` 중심으로 정리한다.
4. Pretendard font resource를 추가하고 일반모드 Theme에 연결한다.
5. 저시력 전용 Typography가 일반모드 변경 영향 없이 유지되는지 확인한다.

## 9. 결론

### 9.1 current implementation fact

- 현재 앱은 사실상 전역이 온고딕 계열이다.
- 저시력 화면 대부분은 `LowVisionFontTheme`로 보호되지만, `LowVisionVoiceInputRoute`와 `TermsGuideRoute`는 예외다.
- 일반모드 화면에는 `Black` / `ExtraBold` 직접 사용이 일부 남아 있다.
- `Type.kt`의 Typography는 현재 사용 중인 모든 role을 아직 명시적으로 커버하지 않는다.

### 9.2 이번 문서 기준 의사결정

- 일반모드는 Pretendard `400 / 500 / 600 / 700` 체계로 정리한다.
- 저시력모드는 온고딕 유지 전제를 분명히 하고, route wrapper와 Typography 레벨에서 분리한다.
- 일반모드의 `Black` / `ExtraBold`는 기본 정책에서 제외하고, 필요한 곳도 최소 예외로만 남긴다.
- 구현 순서는 `저시력 보호 범위 정리 -> 일반모드 Typography 완성 -> 일반모드 weight override 정리 -> Pretendard 연결`로 가져가는 것이 가장 안전하다.
