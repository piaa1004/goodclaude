# 에러 응답 / 에러 코드

## 응답 형식 (ProblemDetail, RFC 9457)

모든 에러는 `application/problem+json`으로 반환한다. 표준 필드에 확장 속성 `code`를 항상 포함한다.

- `type`은 Spring 기본 직렬화 동작에 따라 기본값(`about:blank`)일 때 생략된다. RFC 9457에서 `type`이 없으면 `about:blank`로 간주하므로 별도 커스터마이징하지 않는다.

```json
{
  "title": "Not Found",
  "status": 404,
  "detail": "게시글을 찾을 수 없습니다.",
  "instance": "/api/v1/posts/999",
  "code": "POST_NOT_FOUND"
}
```

검증 실패(`INVALID_INPUT`)는 `errors` 확장 속성에 필드별 에러를 담는다.

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "입력값이 올바르지 않습니다.",
  "instance": "/api/v1/posts",
  "code": "INVALID_INPUT",
  "errors": [
    { "field": "title", "message": "제목은 1~100자여야 합니다." },
    { "field": "author", "message": "작성자는 필수입니다." }
  ]
}
```

## 에러 코드 목록

### 공통 (`global.error.ErrorCode`)

| code | HTTP | detail | 발생 상황 |
|------|------|--------|-----------|
| `INVALID_INPUT` | 400 | 입력값이 올바르지 않습니다. | `@Valid` 실패 (`MethodArgumentNotValidException`, `HandlerMethodValidationException`) |
| `INVALID_PARAMETER` | 400 | 요청 파라미터가 올바르지 않습니다. | 타입 불일치(`MethodArgumentTypeMismatchException`), 필수 파라미터/헤더/파트 누락(`ServletRequestBindingException`, `MissingServletRequestPartException`), enum 변환 실패 |
| `MALFORMED_JSON` | 400 | 요청 본문을 읽을 수 없습니다. | `HttpMessageNotReadableException` |
| `INVALID_SORT_PROPERTY` | 400 | 지원하지 않는 정렬 기준입니다. | 화이트리스트 외 `sort` 필드 |
| `RESOURCE_NOT_FOUND` | 404 | 요청한 경로를 찾을 수 없습니다. | 매핑되지 않은 URL (`NoResourceFoundException`, `NoHandlerFoundException`) |
| `METHOD_NOT_ALLOWED` | 405 | 지원하지 않는 HTTP 메서드입니다. | `HttpRequestMethodNotSupportedException` |
| `NOT_ACCEPTABLE` | 406 | 요청한 응답 형식을 지원하지 않습니다. | `Accept` 헤더 형식으로 응답 불가 (`HttpMediaTypeNotAcceptableException`) |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | 지원하지 않는 Content-Type입니다. | 요청 `Content-Type` 미지원 (`HttpMediaTypeNotSupportedException`) |
| `INTERNAL_ERROR` | 500 | 서버 내부 오류가 발생했습니다. | 처리되지 않은 모든 예외, 5xx 프레임워크 예외(`MissingPathVariableException`, `ConversionNotSupportedException` 등) |

### 게시글 (`post`)

| code | HTTP | detail |
|------|------|--------|
| `POST_NOT_FOUND` | 404 | 게시글을 찾을 수 없습니다. |

### 댓글 (`comment`)

| code | HTTP | detail |
|------|------|--------|
| `COMMENT_NOT_FOUND` | 404 | 댓글을 찾을 수 없습니다. |

## 구현 규칙

- `ErrorCode`는 `HttpStatus`, `code`(enum name), `detail` 메시지를 가진다. 도메인 코드도 하나의 enum에 모은다 (v1 규모 기준).
- 비즈니스 예외는 `BusinessException(ErrorCode)` 하나를 공통으로 쓰고, 자주 쓰는 경우만 하위 클래스(`PostNotFoundException` 등)를 둔다.
- `GlobalExceptionHandler`는 `ResponseEntityExceptionHandler`를 상속해 프레임워크 예외를 처리하고, 각 응답에 `code`를 채운다.
- `INTERNAL_ERROR`의 `detail`에는 고정 메시지만 쓴다. 원본 예외 메시지는 로그(`ERROR`, 스택트레이스 포함)에만 남긴다.
- `BusinessException`은 `WARN` 레벨로 스택트레이스 없이 로깅한다.
- 새 에러 코드를 추가하면 이 문서의 표를 함께 갱신한다.
- **모든 에러 응답에 `code`는 필수다.** 프레임워크 4xx 예외는 원인별로 위 표의 코드에 매핑하고, 일괄적으로 `INVALID_PARAMETER`로 묶지 않는다.
- 표에 없는 새로운 4xx가 실제로 필요해지면 전용 에러 코드를 추가하고 이 표를 갱신한다. 매핑이 누락된 예외는 `GlobalExceptionHandler`가 `ERROR` 로그(`ErrorCode mapping missing`)로 남기므로, 발견 즉시 코드를 추가한다.
- 첨부파일이 v1 범위 밖이므로 multipart 파싱을 끈다(`spring.servlet.multipart.enabled: false`). 켜 두면 업로드 한도를 넘는 multipart 요청이 모든 경로에서 413(`MaxUploadSizeExceededException`)을 만든다. 끈 상태에서 multipart 요청은 `415 UNSUPPORTED_MEDIA_TYPE`이 된다. 첨부파일 기능을 도입하면 multipart를 다시 켜고 413 전용 코드를 추가한다.
- DispatcherServlet 밖에서 발생한 에러(필터 예외, `sendError`, `/error` 직접 호출)는 `ProblemDetailErrorController`가 Boot 기본 `BasicErrorController`를 대체해 `ProblemDetail` + `code`로 응답한다. 상태만으로 원인이 하나로 정해지는 404/405/406/415와 5xx만 매핑하고, `/error` 직접 호출은 `RESOURCE_NOT_FOUND`로 처리한다.
- 5xx 프레임워크 예외는 예외 타입과 무관하게 `INTERNAL_ERROR`로 응답한다 (일부 5xx 예외가 400 계열 예외의 하위 타입이므로 상태를 먼저 판별).
- `code` 필수 원칙의 예외: 406처럼 콘텐츠 협상 실패로 응답 본문 자체를 만들 수 없는 경우에는 상태 코드만 반환하는 것을 허용한다. 이를 위해 응답 형식을 강제로 우회하지 않는다. (Spring은 `ProblemDetail`을 `problem+json`으로 쓸 수 있으면 본문을 쓴다.)
