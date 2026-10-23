# 구현 로드맵

Phase 순서대로 진행한다. 한 번에 하나의 Phase만 작업하고, 완료한 항목은 `[x]`로 체크한다. 각 Phase는 **완료 조건**을 모두 만족해야 끝난 것으로 본다.

## Phase 0 — 프로젝트 기반

- [x] Gradle Kotlin DSL 프로젝트 생성 (Java 21 toolchain, Spring Boot 4.x)
- [x] 의존성: CLAUDE.md의 "Boot 4 모듈화 — starter 명명 규칙" 표 기준
- [x] `application.yml` / `application-local.yml` / `application-docker.yml` / `application-test.yml`
- [x] `Dockerfile` (멀티 스테이지, 의존성 캐시, layered extract, non-root)
- [x] `.dockerignore`, `docker-compose.yml` (db healthcheck 포함), `.env.example`
- [x] `global/entity/BaseEntity`, `global/config/JpaAuditingConfig`
- [x] `global/error`: `ErrorCode`, `BusinessException`, `GlobalExceptionHandler` (공통 코드 전체)
- [x] `global/common/PageResponse`
- [x] `test/.../support`: Testcontainers `@ServiceConnection` 공통 설정
- [x] 컨텍스트 로딩 테스트 (`@SpringBootTest` + Testcontainers)

**완료 조건**
- `./gradlew build` 통과
- `docker compose up --build -d` 후 app 컨테이너가 정상 기동 (Flyway 마이그레이션 0건이어도 OK)

## Phase 1 — 게시글 CRUD

- [x] `V1__create_posts.sql` (`docs/domain-model.md` 기준)
- [x] `Post` 엔티티, `PostRepository`
- [x] DTO: `PostCreateRequest`, `PostUpdateRequest`, `PostDetailResponse`, `PostSummaryResponse`
- [x] `PostService`: 작성, 단건 조회(원자적 조회수 증가), 목록(페이징), 수정, 삭제
- [x] `PostController` (`docs/api-spec.md` 기준, 201 + Location / 204)
- [x] `POST_NOT_FOUND` 에러 처리
- [x] 테스트
  - [x] `PostRepositoryTest` (Testcontainers): 조회수 증가 쿼리, 목록 페이징
  - [x] `PostServiceTest` (Mockito)
  - [x] `PostControllerTest` (`@WebMvcTest` + `MockMvcTester`): 정상/검증 실패/404

**완료 조건**
- `./gradlew build` 통과
- 목록 조회 시 실행 쿼리가 페이지당 2개(목록 + count) 이하

## Phase 2 — 댓글

- [ ] `V2__create_comments.sql`
- [ ] `Comment` 엔티티 (`@ManyToOne(LAZY)`, `@OnDelete(CASCADE)`), `CommentRepository`
- [ ] DTO: `CommentCreateRequest`, `CommentUpdateRequest`, `CommentResponse`
- [ ] `CommentService`: 작성, 목록(id asc 고정), 수정, 삭제, 게시글 소속 검증
- [ ] `CommentController`
- [ ] 게시글 목록에 `commentCount` 추가 (단일 쿼리, N+1 없음)
- [ ] `COMMENT_NOT_FOUND` 에러 처리
- [ ] 테스트
  - [ ] 게시글 삭제 시 댓글 cascade 삭제 (Repository/통합)
  - [ ] 다른 게시글의 `commentId`로 수정/삭제 시 404
  - [ ] `commentCount` 정확성

**완료 조건**
- `./gradlew build` 통과
- 게시글 목록 조회가 댓글 수 포함해서도 쿼리 2개(목록 + count) 이하

## Phase 3 — 검색 / 정렬

- [ ] `PostSearchCondition` (`searchType`, `keyword`)
- [ ] 검색 쿼리 (대소문자 무시 부분 일치, `%`/`_` escape)
- [ ] 정렬 화이트리스트 (`createdAt`, `viewCount`) + `INVALID_SORT_PROPERTY`
- [ ] 페이지 크기 상한 100 (`spring.data.web.pageable.max-page-size`)
- [ ] 테스트: 검색 타입별, 특수문자 keyword, 허용되지 않은 sort

**완료 조건**
- `./gradlew build` 통과
- `docs/api-spec.md`의 목록 API 파라미터가 모두 동작

## Phase 4 — 회원 / 인증 (미정)

착수 전 결정 필요 사항. 결정되면 CLAUDE.md "인증/인가" 섹션과 이 Phase를 갱신한다.

- [ ] 인증 방식 결정 (세션 vs JWT)
- [ ] `members` 테이블 및 `posts.author`, `comments.author` → `member_id` FK 마이그레이션 전략
- [ ] 작성자 본인만 수정/삭제 가능하도록 권한 검증
- [ ] 비밀번호 해시 (`PasswordEncoder`)

> Phase 4 결정 전에는 Spring Security 의존성을 추가하지 않는다.
