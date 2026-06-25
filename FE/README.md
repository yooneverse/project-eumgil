<div align="center">

# 부산이음길 Frontend

### Busan EumGil Android

**부산 지역 이동 약자를 위한 무장애 길찾기 Android 앱**

<br>

[![Android](https://img.shields.io/badge/Android-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Material 3](https://img.shields.io/badge/Material_3-757575?style=flat-square&logo=materialdesign&logoColor=white)](https://m3.material.io)
[![Kakao Map](https://img.shields.io/badge/Kakao_Map-FFCD00?style=flat-square&logo=kakao&logoColor=000000)](https://apis.map.kakao.com/android_v2)

</div>

---

> 프로젝트 전체 개요, 운영 구조, Git/Jira 컨벤션은 루트 [README.md](../README.md)를 기준으로 합니다.

## 프로젝트 소개

**부산이음길 Frontend**는 부산 지역 이동 약자를 위한 무장애 길찾기 서비스를 Android 앱으로 제공하는 클라이언트입니다.

일반 길찾기 앱이 거리와 소요 시간 중심으로 경로를 안내한다면, 부산이음길 앱은 경사, 계단, 엘리베이터, 점자블록, 접근성 시설처럼 이동 약자의 실제 이동 가능성에 영향을 주는 정보를 화면과 음성으로 함께 전달하는 것을 목표로 합니다.

또한 소셜 로그인, 사용자 유형, 온보딩 상태를 기준으로 앱 진입을 분기하고, 서버 API와 연동되는 경로 탐색, 즐겨찾기, 제보, 사용자 설정 흐름을 Android 환경에 맞게 제공합니다.

### 핵심 사용자

- 시각장애인
- 휠체어 이용자
- 고령자
- 유아차 동반 보호자
- 일시적 이동 불편자

### 핵심 기능

- **접근성 중심 UI**: 큰 터치 영역, 명확한 CTA, 큰글씨 설정, 저시력 전용 화면 흐름 제공
- **무장애 경로 안내**: 경로 단계, 방향 안내, 구간별 세그먼트, 도착 안내를 앱 화면에 맞게 구성
- **지도 기반 정보 탐색**: Kakao Map SDK 기반 현재 위치, 접근성 시설, 제보 위치, 경로 표시
- **음성 기반 사용 흐름**: TTS 안내와 sherpa-onnx 기반 on-device STT 입력 흐름 지원
- **인증 기반 사용성**: 로그인 세션, 사용자 유형, 온보딩 상태에 따라 홈과 주요 기능 진입을 제어
- **테스트 가능한 화면 정책**: 라우팅, ViewModel, DTO 매핑, 화면 정책을 단위 테스트로 검증

### 핵심 흐름

```text
앱 실행
  -> 권한 / 온보딩 / 사용자 모드 설정
  -> 현재 위치 및 목적지 입력
  -> 접근성 기반 경로 조회
  -> 지도 표시 + 단계별 안내 + 음성 안내
  -> 도착 / 저장 / 제보 / 마이페이지 흐름
```

---

## 주요 문서

- FE 코드 컨벤션: [Docs/2026-04-13_부산이음길_FE_코드_컨벤션.md](Docs/2026-04-13_부산이음길_FE_코드_컨벤션.md)
- FE 컴포넌트 가이드: [Docs/2026-04-13_부산이음길_FE_컴포넌트_가이드.md](Docs/2026-04-13_부산이음길_FE_컴포넌트_가이드.md)
- FE 디자인 컨벤션: [Docs/2026-04-22_부산이음길_FE_디자인_컨벤션.md](Docs/2026-04-22_부산이음길_FE_디자인_컨벤션.md)
- FE 화면 인벤토리 및 라우트 맵: [Docs/2026-04-22_부산이음길_FE_화면_인벤토리_및_라우트_맵.md](Docs/2026-04-22_부산이음길_FE_화면_인벤토리_및_라우트_맵.md)
- FE 접근성 정보 라벨 가이드: [Docs/2026-04-24_부산이음길_FE_접근성_정보_라벨_가이드.md](Docs/2026-04-24_부산이음길_FE_접근성_정보_라벨_가이드.md)
- 프로젝트 전체 README: [../README.md](../README.md)

---

## 기술 스택

| 분류 | 기술 |
|---|---|
| Platform | Android |
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Architecture | Single Activity, Navigation Compose, ViewModel |
| Map | Kakao Map SDK |
| Local Data | Room, DataStore |
| Auth / SDK | Kakao SDK, Naver OAuth, Google Play Services |
| Voice | Android TTS, sherpa-onnx on-device STT |
| Image | Coil |
| Test | JUnit, Coroutine Test, Compose UI policy tests |

---

## 저장소 구조

```text
FE/
├── app/
│   ├── src/main/java/com/ssafy/e102/eumgil/
│   │   ├── app/          # 앱 진입점, 전역 내비게이션
│   │   ├── core/         # 공통 모델과 기반 코드
│   │   ├── data/         # remote, local, mock, repository
│   │   └── feature/      # 화면 단위 기능 모듈
│   ├── src/test/         # 단위 테스트
│   └── build.gradle.kts
├── Docs/                 # FE 설계, 컨벤션, QA, 디버깅 문서
├── gradle/               # Gradle wrapper 파일
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew.bat
```

### 디렉토리 책임

- `app/`
  - 앱 진입점, 전역 내비게이션, Android 애플리케이션 설정
- `core/`
  - 기능 전반에서 공유하는 모델과 기반 코드
- `data/`
  - API DTO, local DB, mock datasource, repository 구현
- `feature/`
  - 화면 단위 Compose UI, Route, ViewModel, Contract
- `Docs/`
  - FE 개발 기준, 디자인 기준, QA 기록, 디버깅 기록

### 설정 파일 배치 원칙

- 앱별 로컬 설정은 `FE/app.local.properties`에서 관리합니다.
- API base URL, SDK key, demo/mock mode 값은 Gradle property 또는 `app.local.properties`로 주입합니다.
- secret 값은 저장소 문서에 직접 적지 않고 필요한 key 이름만 안내합니다.
- Gradle/Android 테스트 캐시는 새 임시 폴더를 만들지 않고 고정 경로를 재사용합니다.

---

## 현재 프론트엔드 아키텍처 결정

### 앱 구조

- Single Activity 기반 Android 앱입니다.
- Jetpack Compose로 화면을 구성합니다.
- Navigation Compose로 화면 이동과 top-level route를 관리합니다.
- feature 단위로 `Route`, `Screen`, `ViewModel`, `Contract`를 분리합니다.

### 상태 관리 원칙

- 화면 상태는 `UiState`로 표현합니다.
- 사용자 입력과 화면 액션은 `UiAction` 또는 feature contract로 전달합니다.
- 스낵바, 화면 이동, 외부 intent 같은 일회성 효과는 `UiEvent`로 분리합니다.
- 데이터 접근은 repository를 통해 remote, local, mock datasource를 교체 가능하게 유지합니다.

### 접근성 / UX 원칙

- 이동 약자의 실제 이동 가능성을 일반적인 최단 거리보다 우선합니다.
- 저시력 모드는 일반 화면의 단순 확대가 아니라 별도 화면 흐름으로 관리합니다.
- 지도, 경로, 음성 안내는 사용자가 현재 위치와 다음 행동을 즉시 이해할 수 있도록 구성합니다.
- 화면 문구, 색상, 컴포넌트 구성은 FE 디자인 컨벤션과 접근성 정보 라벨 가이드를 기준으로 맞춥니다.

---

## 시작하기

### 1. 클론

```bash
git clone <repository-url>
cd S14P31E102/FE
```

### 2. 개발 환경

- Android Studio
- JDK 17
- Android SDK 34
- Gradle wrapper

### 3. 로컬 설정

`FE/app.local.properties`에 필요한 값을 설정합니다.

```properties
app.debug.baseUrl=https://api.dev.busaneumgil.com/
app.debug.mockMode=false
app.debug.demoMode=false
app.debug.forceLowVisionTermsGuide=false
app.debug.kakaoNativeAppKey=
app.debug.naverClientId=
app.debug.naverClientSecret=
app.debug.naverClientName=BusanEumGil
```

release 빌드에서 별도 값이 필요하면 아래 key를 추가로 설정합니다.

```properties
app.release.baseUrl=
app.release.kakaoNativeAppKey=
app.release.naverClientId=
app.release.naverClientSecret=
app.release.naverClientName=
```

### 4. 실행

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon
```

### 5. 테스트

FE Gradle/Android 테스트는 고정 캐시 경로를 재사용합니다.

```powershell
$env:GRADLE_USER_HOME="C:\tmp\codex-gradle-home"
$env:ANDROID_USER_HOME="C:\tmp\codex-android-home"
Remove-Item Env:ANDROID_SDK_HOME -ErrorAction SilentlyContinue
Remove-Item Env:ANDROID_PREFS_ROOT -ErrorAction SilentlyContinue

.\gradlew.bat :app:testDebugUnitTest --no-daemon
.\gradlew.bat --stop
```

특정 테스트만 실행할 때는 아래 형식을 사용합니다.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "패키지명.테스트클래스명" --no-daemon
.\gradlew.bat --stop
```
