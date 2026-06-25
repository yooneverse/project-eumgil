# BE Auth/User 진행상황

> 작성일: 2026-04-30
> 최종 수정일: 2026-05-04
> 브랜치: `be/feat/auth-S14P31E102-364`

## 현재 브랜치 상태

- 이 브랜치는 Auth/User foundation, security/token/social login, session/user API 작업 위에 쌓인 stacked branch이다.
- MR1, MR2가 develop에 먼저 머지되면, 이 브랜치는 develop 기준으로 rebase 또는 retarget 정리가 필요하다.
- 현재 브랜치에는 MR3 대상 작업과 함께 auth/security/token 패키지 정리, 테스트 페이지 보강, 문서 정합성 보강이 포함되어 있다.

## 완료한 작업

### MR1: Auth/User Foundation

- `S14P31E102-371`: Auth/User 도메인 예외 구조 추가
  - `AuthException`, `AuthErrorCode`
  - `UserException`, `UserErrorCode`
- `S14P31E102-367`: User 도메인 기반 추가
  - `User` 엔티티
  - `SocialProvider`, `PrimaryUserType`, `MobilitySubtype`
  - `UserRepository`
  - User 엔티티 검증 테스트

### MR2: Token/Security/Social Login

- `S14P31E102-368`: Spring Security 기본 구조 구현
  - stateless security filter chain
  - 인증 실패/인가 실패 JSON 응답
  - 인증 필요 endpoint와 public endpoint 분리
- `S14P31E102-369`: JWT 발급/검증 및 UUID subject 처리 구현
  - access token, refresh token, signup token 발급/검증
  - service token은 UUID subject 사용
  - signup token은 social provider identity를 claim으로 보관
  - bearer token 기반 `AuthPrincipal` 주입
- `S14P31E102-370`: refresh/signup token 저장 전략 구현
  - Redis 기반 refresh token store
  - Redis 기반 signup token store
  - raw token을 Redis key로 쓰지 않고 SHA-256 hash를 key suffix로 사용
  - refresh token rotation 지원
- `S14P31E102-372`: Kakao/Naver/Google social token verifier 구현
  - FE가 전달한 provider access token으로 user-info API 호출
  - provider 응답을 `SocialUserInfo`로 정규화
  - provider 4xx는 `INVALID_SOCIAL_TOKEN`
  - provider 5xx/호출 실패/응답 이상은 `SOCIAL_PROVIDER_API_FAILED`
- `S14P31E102-373`: `POST /auth/social-login` 기존/신규 사용자 흐름 구현
  - 기존 가입 완료 사용자는 access token, refresh token, userId, 사용자 유형을 반환
  - 신규 소셜 사용자는 `users` row를 만들지 않고 signup token만 반환
  - refresh token은 Redis 저장소에 저장
  - signup token은 Redis 저장소에 social provider identity와 함께 저장
- `S14P31E102-374`: `POST /auth/signup` 회원가입 완료 흐름 구현
  - signup token JWT와 Redis 저장소 값을 함께 검증
  - 필수 약관 동의와 사용자 유형 조합 검증
  - 가입 완료 시점에만 `users` row 생성
  - 가입 완료 후 signup token 삭제 및 service token 발급
- `S14P31E102-375`: `POST /auth/reissue` refresh token rotation 구현
  - refresh token subject와 Redis 저장소 사용자 ID를 함께 검증
  - 기존 refresh token 삭제 후 새 refresh token 저장
  - 재발급 실패는 `INVALID_REFRESH_TOKEN`으로 매핑

### MR3: Session/User API/Test/Swagger

- `S14P31E102-364`: 로그아웃과 회원탈퇴 세션 무효화 구현
  - 로그아웃 시 해당 사용자의 refresh token을 삭제하고 현재 access token을 blacklist에 등록
  - 회원탈퇴 시 `users` row를 물리 삭제하고 남은 인증 세션을 무효화
  - 성공 응답은 `200 OK`와 `ApiResponse` body로 통일
- `S14P31E102-376`: 내 정보 조회 API 구현
  - `GET /users/me`
  - `@AuthenticationPrincipal AuthPrincipal` 기준으로 현재 사용자 조회
- `S14P31E102-377`: 사용자 유형 수정 API 구현
  - `PATCH /users/me/user-type`
  - 저시력자/보행약자 사용자 유형 조합 검증 재사용
- `S14P31E102-378`, `S14P31E102-379`, `S14P31E102-380`: 테스트, 테스트 페이지, 문서/Swagger 정합성 확인
  - controller/service/security/token 테스트 보강
  - `auth-test.html` local/dev 전용 접근 제어와 실제 소셜 로그인 버튼 보강
  - 로그아웃/회원탈퇴 성공/실패 메시지 표시
  - 없는 정적/API 경로가 500이 아니라 404로 내려가도록 전역 예외 처리 보강
- 구조 정리:
  - `global.security.config/filter/handler/jwt/principal` 하위 패키지로 security 파일 분리
  - auth social verifier를 `domain.auth.social` 하위로 이동
  - refresh/signup/access blacklist 저장소를 `AuthTokenStore`로 통합
  - 테스트 페이지 지원 코드를 `global.test.auth` 하위로 이동

## 검증 내역

- `cd BE && .\gradlew.bat test --tests com.ssafy.e102.global.security.config.SecurityConfigTest`
- `cd BE && .\gradlew.bat test --tests com.ssafy.e102.global.security.jwt.JwtTokenProviderTest --tests com.ssafy.e102.global.security.config.SecurityConfigTest`
- `cd BE && .\gradlew.bat test --tests com.ssafy.e102.domain.auth.token.RedisAuthTokenStoreTest`
- `cd BE && .\gradlew.bat test --tests com.ssafy.e102.domain.auth.social.verifier.SocialTokenVerifierTest --tests com.ssafy.e102.domain.auth.social.verifier.CompositeSocialTokenVerifierTest`
- `cd BE && .\gradlew.bat test --tests com.ssafy.e102.domain.auth.service.AuthServiceSocialLoginTest --tests com.ssafy.e102.domain.auth.controller.AuthControllerTest`
- `cd BE && .\gradlew.bat test --tests com.ssafy.e102.domain.auth.service.AuthServiceSignupTest --tests com.ssafy.e102.domain.auth.controller.AuthControllerTest`
- `cd BE && .\gradlew.bat test --tests com.ssafy.e102.domain.auth.service.AuthServiceReissueTest --tests com.ssafy.e102.domain.auth.controller.AuthControllerTest`
- `cd BE && .\gradlew.bat test --tests com.ssafy.e102.domain.user.controller.UserControllerTest --tests com.ssafy.e102.domain.user.service.UserServiceProfileTest`
- `cd BE && .\gradlew.bat processResources test`
- `cd BE && .\gradlew.bat spotlessApply test`
- `GET http://localhost:8080/v3/api-docs`: auth/user endpoints 노출 확인
- `POST /auth/logout`, `DELETE /users/me`: 200 응답 body 수동 확인
- `cd BE && .\gradlew.bat spotlessJavaCheck`
- `cd BE && .\gradlew.bat checkstyleMain checkstyleTest`
- `cd BE && .\gradlew.bat test`
- `git diff --check`

## MR 계획

### MR1: Auth/User Foundation

- 대상 브랜치: `be/feat/auth-S14P31E102-362`
- 대상 Jira:
  - `S14P31E102-367`
  - `S14P31E102-371`
- MR 제목:
  - `S14P31E102-362 [BE] Auth/User 도메인 기반 구조 추가`
- 리뷰 포인트:
  - User 엔티티가 최신 ERD/API 계약과 맞는지
  - `profileCompleted` 같은 불필요한 persisted 상태를 추가하지 않았는지
  - domain별 exception/error code 구조가 팀 컨벤션으로 적절한지

### MR2: Token, Security, Social Login Flow

- 대상 브랜치: `be/feat/auth-S14P31E102-368`
- 대상 Jira:
  - `S14P31E102-368`
  - `S14P31E102-369`
  - `S14P31E102-370`
  - `S14P31E102-372`
  - `S14P31E102-373`
  - `S14P31E102-374`
- 현재 완료:
  - `S14P31E102-368`
  - `S14P31E102-369`
  - `S14P31E102-370`
  - `S14P31E102-372`
  - `S14P31E102-373`
  - `S14P31E102-374`
- 남은 작업:
  - MR2 기능 구현은 완료. MR 생성/갱신 전 최종 리뷰와 필요 시 Swagger 문서 보강 필요
- MR 제목:
  - `S14P31E102-362 [BE] 소셜 로그인 및 회원가입 토큰 흐름 구현`
- 리뷰 포인트:
  - service token subject가 UUID인지
  - 신규 소셜 사용자가 signup 전 `users` row를 만들지 않는지
  - provider 응답이 service layer로 직접 새지 않고 `SocialUserInfo`로 정규화되는지
  - invalid provider token과 provider 장애가 다른 error code로 내려가는지

### MR3: Session/User API/Test/Swagger

- 대상 Jira:
  - `S14P31E102-364`
  - `S14P31E102-375`
  - `S14P31E102-376`
  - `S14P31E102-377`
  - `S14P31E102-378`
  - `S14P31E102-379`
  - `S14P31E102-380`
- 현재 완료:
  - `POST /auth/reissue`
  - `POST /auth/logout`
  - `GET /users/me`
  - `PATCH /users/me/user-type`
  - `DELETE /users/me`
  - controller/service/security/token/failure path 테스트
  - `auth-test.html` local/dev 전용 테스트 페이지와 수동 검증 흐름
  - Swagger/OpenAPI endpoint 노출 확인
- MR 제목:
  - `S14P31E102-364 [BE] 토큰 재발급 로그아웃 사용자 API 및 테스트 구현`

## 남은 결정 사항

- 회원탈퇴 정책
  - 현재는 `users` soft delete와 인증 세션 무효화 기준으로 구현
  - bookmark/favorite route/rating/report 테이블이 붙으면 삭제/보존/익명화 정책을 다시 반영해야 한다.
- signup token TTL
  - 현재 기본값은 10분
  - FE 온보딩 흐름 기준으로 충분한지 확인 필요
- provider 운영 범위
  - Kakao/Naver/Google 모두 production-ready로 열지, MVP에서 일부만 우선 운영할지 확인 필요
- MR 병합 순서
  - MR1, MR2가 먼저 머지되어야 MR3 diff가 리뷰 가능하다.
  - 선행 MR 머지 후 MR3는 develop 기준 rebase가 필요할 수 있다.
