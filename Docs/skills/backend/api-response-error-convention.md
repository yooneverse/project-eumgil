# API Response And Error Convention

## 목적

API 공통 응답, 에러 코드, 예외 처리 방식을 통일한다.

API별 request/response 필드, HTTP status, 애플리케이션 응답 코드는 API 명세서를 우선한다.

## 위치

- `ApiResponse`, `ErrorResponse`: `global.response`
- `BusinessException`, `ErrorCode`, `CommonErrorCode`, `GlobalExceptionHandler`: `global.exception`
- 도메인별 에러 코드 enum: `domain.{domain}.exception.{Domain}ErrorCode`

## 성공 응답

```json
{
  "status": "S2000",
  "data": {},
  "message": "정상 처리되었습니다."
}
```

`status`에는 HTTP 상태 코드 숫자가 아니라 애플리케이션 응답 코드를 넣는다. 코드 형식, prefix, 공통 코드 목록은 `Docs/컨벤션/2026-04-14_API_응답_코드_컨벤션.md`를 따른다.

데이터가 없는 성공 응답은 `data = null`을 허용한다.

```java
public record ApiResponse<T>(
        String status,
        T data,
        String message
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("S2000", data, "정상 처리되었습니다.");
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>("S2010", data, "생성되었습니다.");
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>("S2000", null, "정상 처리되었습니다.");
    }
}
```

`204 No Content`는 응답 body를 보내지 않으므로 `ApiResponse`로 감싸지 않는다.

## 실패 응답

```json
{
  "status": "C4000",
  "message": "잘못된 입력입니다."
}
```

```java
public record ErrorResponse(
        String status,
        String message
) {
    public static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getStatus(), errorCode.getMessage());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.getStatus(), message);
    }
}
```

## Error Code

공통 예외 처리는 `GlobalExceptionHandler`에서 처리하되, 비즈니스 에러 코드는 도메인별로 관리한다.

```text
domain
├─ user
│  └─ exception
│     └─ UserErrorCode
├─ place
│  └─ exception
│     └─ PlaceErrorCode
└─ route
   └─ exception
      └─ RouteErrorCode
```

도메인별 enum 이름은 `UserErrorCode`, `PlaceErrorCode`, `RouteErrorCode`처럼 도메인명을 접두사로 붙인다. import 충돌을 줄이고 코드만 봐도 어느 도메인의 에러인지 알 수 있게 하기 위함이다.

공통 `BusinessException`은 도메인별 enum을 직접 알지 않도록 `global.exception.ErrorCode` 인터페이스에 의존한다.

```java
public interface ErrorCode {
    HttpStatus getHttpStatus();
    String getStatus();
    String getMessage();
}
```

```java
public enum UserErrorCode implements ErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U4040", "사용자를 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String status;
    private final String message;
}
```

## 메시지 기준

- 기본 message는 도메인별 `{Domain}ErrorCode`의 공통 문구를 사용한다.
- 상황별 문구가 필요하면 override한다.
- 검증 실패처럼 사용자가 고칠 수 있는 오류는 필드명을 포함할 수 있다.
- 내부 클래스명, SQL, stack trace, API key, 내부 URL은 response message에 넣지 않는다.

예시:

```java
throw new BusinessException(BookmarkErrorCode.BOOKMARK_ALREADY_EXISTS, "이미 북마크한 장소입니다.");
```

## 예외 처리 기준

- Controller에서 try-catch로 에러 응답 생성을 반복하지 않는다.
- `GlobalExceptionHandler`는 전역으로 하나만 둔다.
- 비즈니스 예외는 각 도메인별 `{Domain}ErrorCode`를 담아 `BusinessException`으로 던진다.
- Validation 예외처럼 여러 도메인에 공통으로 적용되는 요청 검증 실패는 `CommonErrorCode.INVALID_INPUT`으로 처리하고 `C4000`으로 응답한다.
- 외부 API 예외는 기본적으로 `CommonErrorCode.EXTERNAL_API_ERROR`로 처리하고 `E5020`으로 응답한다.
- 예상하지 못한 예외는 `CommonErrorCode.INTERNAL_ERROR`로 처리하고 `I5000`으로 응답한다.
- 상세 예외는 서버 로그에 남긴다.

## 로그 기준

- 400 validation 오류는 기본적으로 `log.error`를 남기지 않는다.
- 일반적인 404, 409 비즈니스 예외도 `log.error`를 남기지 않는다.
- 추적이 필요한 비즈니스 예외는 `log.warn`으로 남긴다.
- 외부 API 호출 실패는 `log.warn` 또는 `log.error`로 남긴다.
- 예상하지 못한 500 예외는 `log.error("...", exception)`로 남긴다.
- 로그에 API key와 개인정보를 남기지 않는다.

## Slack 콜백 예외

- Slack 요청 확인에는 빠른 `200 OK` 응답이 필요할 수 있다.
- 공통 응답 wrapper 적용 여부는 Slack 연동 방식에 맞춰 판단한다.
- Slack 서명 검증 실패는 `UNAUTHORIZED`로 처리한다.
- 이미 처리된 제보는 `CONFLICT`로 처리한다.
