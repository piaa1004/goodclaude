# API 명세

- Base path: `/api/v1`
- Content-Type: `application/json`
- 시각 필드: ISO-8601 UTC (`2026-10-23T03:15:00Z`)
- 에러 응답: `ProblemDetail` — 형식과 코드 목록은 `docs/error-codes.md`
- 공통 에러: 모든 엔드포인트는 엔드포인트별 에러 외에 `400 MALFORMED_REQUEST`(컨테이너 단계에서 거부된 요청), `404 RESOURCE_NOT_FOUND`, `405 METHOD_NOT_ALLOWED`, `406 NOT_ACCEPTABLE`, `500 INTERNAL_ERROR`, 그리고 요청 본문이 있는 API는 `400 MALFORMED_JSON`, `415 UNSUPPORTED_MEDIA_TYPE`를 반환할 수 있다

## 엔드포인트 요약

| Method | Path | 설명 | 성공 |
|--------|------|------|------|
| POST | `/api/v1/posts` | 게시글 작성 | 201 |
| GET | `/api/v1/posts` | 게시글 목록/검색 | 200 |
| GET | `/api/v1/posts/{postId}` | 게시글 단건 조회 (조회수 +1) | 200 |
| PUT | `/api/v1/posts/{postId}` | 게시글 수정 | 200 |
| DELETE | `/api/v1/posts/{postId}` | 게시글 삭제 | 204 |
| POST | `/api/v1/posts/{postId}/comments` | 댓글 작성 | 201 |
| GET | `/api/v1/posts/{postId}/comments` | 댓글 목록 | 200 |
| PUT | `/api/v1/posts/{postId}/comments/{commentId}` | 댓글 수정 | 200 |
| DELETE | `/api/v1/posts/{postId}/comments/{commentId}` | 댓글 삭제 | 204 |

## 공통: 페이지 응답

`Page<T>`를 직접 직렬화하지 않고 `PageResponse<T>` record로 변환한다.

```json
{
  "content": [ ... ],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 135,
    "totalPages": 7
  }
}
```

```java
public record PageResponse<T>(List<T> content, PageInfo page) {
    public record PageInfo(int number, int size, long totalElements, int totalPages) {}
    public static <T> PageResponse<T> from(Page<T> page) { /* ... */ }
}
```

---

## 게시글

### POST `/api/v1/posts` — 작성

Request
```json
{
  "title": "첫 글입니다",
  "content": "본문 내용",
  "author": "intheeast"
}
```

Response `201 Created`
- Header: `Location: /api/v1/posts/1`
- Body: `PostDetailResponse`

```json
{
  "id": 1,
  "title": "첫 글입니다",
  "content": "본문 내용",
  "author": "intheeast",
  "viewCount": 0,
  "createdAt": "2026-10-23T03:15:00Z",
  "updatedAt": "2026-10-23T03:15:00Z"
}
```

Errors: `400 INVALID_INPUT`

### GET `/api/v1/posts` — 목록 / 검색

Query parameters

| 이름 | 필수 | 기본값 | 설명 |
|------|------|--------|------|
| `page` | N | 0 | 0부터 시작 |
| `size` | N | 20 | 최대 100 (초과 시 100으로 보정) |
| `sort` | N | `createdAt,desc` | 허용: `createdAt`, `viewCount` |
| `searchType` | N | `TITLE_CONTENT` | `TITLE`, `CONTENT`, `TITLE_CONTENT` |
| `keyword` | N | — | 1~50자, 없으면 전체 목록 |

Response `200 OK` — `PageResponse<PostSummaryResponse>`

```json
{
  "content": [
    {
      "id": 1,
      "title": "첫 글입니다",
      "author": "intheeast",
      "viewCount": 12,
      "commentCount": 3,
      "createdAt": "2026-10-23T03:15:00Z"
    }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 1, "totalPages": 1 }
}
```

Errors: `400 INVALID_SORT_PROPERTY`, `400 INVALID_PARAMETER` (잘못된 `searchType` 등), `400 INVALID_INPUT` (`keyword` 길이 초과)

### GET `/api/v1/posts/{postId}` — 단건 조회

- 호출 시 `viewCount` +1 (원자적 update), 응답에는 증가된 값 반영.

Response `200 OK` — `PostDetailResponse` (작성 응답과 동일 구조)

Errors: `404 POST_NOT_FOUND`, `400 INVALID_PARAMETER` (`postId` 타입 불일치)

### PUT `/api/v1/posts/{postId}` — 수정

Request
```json
{
  "title": "수정된 제목",
  "content": "수정된 본문"
}
```

Response `200 OK` — `PostDetailResponse`

Errors: `400 INVALID_INPUT`, `404 POST_NOT_FOUND`, `400 INVALID_PARAMETER` (`postId` 타입 불일치)

### DELETE `/api/v1/posts/{postId}` — 삭제

Response `204 No Content` (댓글도 함께 삭제)

Errors: `404 POST_NOT_FOUND`, `400 INVALID_PARAMETER` (`postId` 타입 불일치)

---

## 댓글

### POST `/api/v1/posts/{postId}/comments` — 작성

Request
```json
{
  "content": "좋은 글이네요",
  "author": "reader1"
}
```

Response `201 Created`
- Header: `Location: /api/v1/posts/1/comments/10`
- Body: `CommentResponse`

```json
{
  "id": 10,
  "postId": 1,
  "content": "좋은 글이네요",
  "author": "reader1",
  "createdAt": "2026-10-23T03:20:00Z",
  "updatedAt": "2026-10-23T03:20:00Z"
}
```

Errors: `400 INVALID_INPUT`, `404 POST_NOT_FOUND`

### GET `/api/v1/posts/{postId}/comments` — 목록

| 이름 | 필수 | 기본값 | 설명 |
|------|------|--------|------|
| `page` | N | 0 | |
| `size` | N | 20 | 최대 100 |

- 정렬은 작성순(`id,asc`) 고정. `sort` 파라미터는 무시한다.

Response `200 OK` — `PageResponse<CommentResponse>`

Errors: `404 POST_NOT_FOUND`

### PUT `/api/v1/posts/{postId}/comments/{commentId}` — 수정

Request
```json
{ "content": "수정된 댓글" }
```

Response `200 OK` — `CommentResponse`

Errors: `400 INVALID_INPUT`, `404 COMMENT_NOT_FOUND` (댓글이 없거나 `postId` 소속이 아님)

### DELETE `/api/v1/posts/{postId}/comments/{commentId}` — 삭제

Response `204 No Content`

Errors: `404 COMMENT_NOT_FOUND`

---

## DTO 목록

| 패키지 | DTO | 용도 |
|--------|-----|------|
| `post.dto` | `PostCreateRequest` | 작성 요청 |
| `post.dto` | `PostUpdateRequest` | 수정 요청 |
| `post.dto` | `PostSearchCondition` | `searchType` + `keyword` |
| `post.dto` | `PostSummaryResponse` | 목록 항목 (content 제외, commentCount 포함) |
| `post.dto` | `PostDetailResponse` | 단건/작성/수정 응답 |
| `comment.dto` | `CommentCreateRequest` | 작성 요청 |
| `comment.dto` | `CommentUpdateRequest` | 수정 요청 |
| `comment.dto` | `CommentResponse` | 단건/목록 항목 |
| `global.common` | `PageResponse<T>` | 페이지 응답 래퍼 |

요청 DTO의 Bean Validation 규칙은 `docs/requirements.md`의 필드 규칙과 일치시킨다.
