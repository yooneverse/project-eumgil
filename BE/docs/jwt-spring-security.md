# JWT / Spring Security

## 선택 이유

- 모바일 API 중심 구조에서 서버 세션보다 stateless 인증이 수평 확장과 운영 단순성에 유리하다.
- Spring Security 필터 체인을 사용하면 인증, 인가, 예외 응답, CORS를 일관된 계층에서 다룰 수 있다.
- Redis와 결합하면 refresh token, logout blacklist, 회원가입 중간 토큰까지 DB 세션 테이블 없이 제어할 수 있다.

## 서비스에서 맡는 역할

- JWT access token 기반 요청 인증
- role 기반 관리자 API 보호
- refresh token rotation과 logout 처리
- 소셜 로그인 이후 가입 전후 상태 관리

## 핵심 구현 방식

### 1. stateless 필터 체인

- `SecurityConfig`는 세션을 사용하지 않는 stateless 구조다.
- JWT 인증 필터를 `UsernamePasswordAuthenticationFilter` 앞에 둬 access token을 먼저 해석한다.
- `/admin/**`는 `ADMIN` 권한이 필요하고, 일반 사용자 API도 인증 여부에 따라 분기한다.

### 2. Redis 기반 토큰 저장

- `RedisAuthTokenStore`가 refresh token, signup token, access blacklist를 저장한다.
- refresh token은 hash로 저장해 저장소 유출 시 원문 노출을 줄였다.
- 로그아웃이나 강제 만료는 blacklist TTL로 처리한다.

### 3. refresh rotation

- `AuthService`는 refresh 재발급 때 기존 토큰을 교체한다.
- 교체는 Redis Lua script를 사용해 old token 제거와 new token 저장을 원자적으로 수행한다.
- 탈취된 이전 토큰 재사용과 동시 요청으로 인한 꼬임을 줄이는 목적이다.

### 4. API 계약 보존

- CORS 허용 헤더에 `Authorization`, `Content-Type`, `Idempotency-Key`를 포함해 인증과 멱등 처리 요청이 함께 동작하도록 맞췄다.
- 인증 실패와 권한 실패는 공통 에러 포맷으로 내려가게 정리했다.

## 예외 처리와 동시성 대응

### 토큰 재발급 레이스 컨디션 완화

- refresh rotation을 원자화하지 않으면 거의 동시에 들어온 두 요청이 서로 다른 최신 토큰을 만들 수 있다.
- Lua script 기반 교체로 단일 성공 흐름만 인정하도록 했다.

### 세션 저장소 없이 강제 만료 지원

- access token은 stateless지만 blacklist를 두어 로그아웃, 탈퇴, 강제 차단 시점을 제어한다.
- refresh token TTL과 blacklist TTL을 분리해 필요한 기간만 저장한다.

### 운영 중 장애 가시성

- 토큰 저장소 예외는 도메인 예외로 감싸 인증 실패와 인프라 실패를 구분할 수 있게 했다.
- 환경 변수 누락은 초기화 단계에서 드러나도록 구성했다.

## 한계와 후속 과제

- stateless 인증은 access token 즉시 폐기가 본질적으로 어렵기 때문에 blacklist 비용을 계속 관리해야 한다.
- 기기 단위 세션 관리, refresh token device binding, 이상 로그인 탐지는 추가 보강 포인트다.
