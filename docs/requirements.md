# 기능 요구사항

게시판 백엔드 API의 기능 범위와 비즈니스 규칙을 정의한다. 구현 순서는 `docs/roadmap.md`, API 상세는 `docs/api-spec.md`를 따른다.

## 범위

| 구분 | v1 포함 | v1 제외 (추후) |
|------|---------|----------------|
| 게시글 | 작성, 단건 조회, 목록 조회(페이징/정렬), 검색, 수정, 삭제 | 첨부파일, 카테고리, 좋아요 |
| 댓글 | 작성, 게시글별 목록 조회(페이징), 수정, 삭제 | 대댓글 |
| 회원/인증 | — | 회원가입, 로그인, 작성자 권한 검증 (Phase 4) |

> v1에는 인증이 없다. 작성자는 `author` 문자열(닉네임)로만 저장하며, 수정/삭제 시 작성자 검증을 하지 않는다. Phase 4에서 회원(`members`) 도입 시 `author`를 회원 FK로 대체한다.

## 게시글 (Post)

### 필드 규칙

| 필드 | 타입 | 규칙 |
|------|------|------|
| `title` | String | 필수, 공백 불가, 1~100자 |
| `content` | String | 필수, 공백 불가, 1~10,000자 |
| `author` | String | 필수, 공백 불가, 1~20자. 작성 후 변경 불가 |
| `viewCount` | long | 0부터 시작, 서버에서만 증가 |
| `createdAt` / `updatedAt` | Instant | JPA Auditing으로 자동 설정 |

### 기능

1. **작성** — 제목, 본문, 작성자를 받아 생성한다.
2. **단건 조회** — 조회 시 `viewCount`를 1 증가시킨다.
   - 동시 요청에서 증가분이 유실되지 않도록 **DB 원자적 update 쿼리**(`view_count = view_count + 1`)로 처리한다. 엔티티를 읽어 `+1` 후 dirty checking하는 방식은 금지(lost update 발생).
   - 응답의 `viewCount`는 증가가 반영된 값이어야 한다.
3. **목록 조회**
   - 페이징 필수. 기본 `page=0`, `size=20`, 최대 `size=100`.
   - 정렬 허용 필드: `createdAt`, `viewCount`. 기본 정렬은 `createdAt,desc`. 그 외 필드는 `INVALID_SORT_PROPERTY` 에러.
   - 목록 항목에는 본문(`content`)을 포함하지 않고, 댓글 수(`commentCount`)를 포함한다.
   - `commentCount`는 게시글마다 별도 쿼리로 세지 않는다 (N+1 금지). 목록 쿼리 하나에서 서브쿼리/그룹 집계로 함께 가져온다.
4. **검색** — 목록 조회 API에 `searchType`, `keyword` 파라미터로 통합한다.
   - `searchType`: `TITLE`, `CONTENT`, `TITLE_CONTENT` (기본 `TITLE_CONTENT`)
   - `keyword`: 1~50자. 대소문자 구분 없이 부분 일치.
   - `keyword`가 없거나 공백이면 검색 조건 없이 전체 목록.
5. **수정** — 제목과 본문을 함께 수정한다 (`PUT`, 둘 다 필수). `author`는 수정 불가.
6. **삭제** — 게시글 삭제 시 해당 게시글의 댓글도 함께 삭제된다 (DB `ON DELETE CASCADE`).

## 댓글 (Comment)

### 필드 규칙

| 필드 | 타입 | 규칙 |
|------|------|------|
| `content` | String | 필수, 공백 불가, 1~1,000자 |
| `author` | String | 필수, 공백 불가, 1~20자. 작성 후 변경 불가 |
| `postId` | Long | 존재하는 게시글이어야 함 |
| `createdAt` / `updatedAt` | Instant | JPA Auditing으로 자동 설정 |

### 기능

1. **작성** — 존재하지 않는 게시글이면 `POST_NOT_FOUND`.
2. **목록 조회** — 게시글별 페이징. 정렬은 작성순(`id,asc`) 고정, 정렬 파라미터는 무시. 기본 `size=20`, 최대 `size=100`. 게시글이 없으면 `POST_NOT_FOUND`.
3. **수정** — 본문만 수정 가능.
4. **삭제**
5. **소속 검증** — 수정/삭제 시 경로의 `postId`와 댓글의 실제 게시글이 다르면 `COMMENT_NOT_FOUND`로 처리한다 (다른 게시글의 댓글 존재 여부를 노출하지 않음).

## 비기능 요구사항

- 모든 시각은 UTC 기준 `Instant`로 저장하고 ISO-8601(`2026-10-23T03:15:00Z`) 형식으로 응답한다.
- 에러 응답은 `ProblemDetail` 형식으로 통일한다 (`docs/error-codes.md`).
- 존재하지 않는 리소스 조회 시 404, 검증 실패 시 400을 반환한다. 500 응답에 내부 예외 메시지나 스택트레이스를 노출하지 않는다.
