---
name: new-domain-feature
description: 게시판 프로젝트에 새 도메인(엔티티 + CRUD API)이나 기존 도메인의 새 기능을 추가할 때 사용. 마이그레이션부터 테스트, 문서 갱신까지 정해진 순서로 진행한다.
---

# 새 도메인 기능 추가

`docs/roadmap.md`의 현재 Phase 항목을 구현할 때 이 순서를 따른다. 단계를 건너뛰지 않는다.

## 0. 사전 확인

- `docs/requirements.md`, `docs/api-spec.md`, `docs/domain-model.md`, `docs/error-codes.md`에서 해당 기능의 명세를 읽는다.
- 명세에 없는 필드/엔드포인트/에러 코드가 필요하면 **구현 전에 사용자에게 확인**한다. 임의로 추가하지 않는다.

## 1. 마이그레이션

- `add-flyway-migration` 스킬을 따른다.
- DDL은 `docs/domain-model.md`의 정의와 일치해야 한다.

## 2. 엔티티

- 위치: `{domain}/domain/`
- `BaseEntity` 상속, `@NoArgsConstructor(access = PROTECTED)`, `@Getter`만.
- 생성은 정적 팩토리(`create(...)`), 변경은 의미 있는 도메인 메서드(`update(...)`).
- `@Column`의 `length`/`nullable`을 DDL과 일치시킨다 (`ddl-auto: validate`로 검증됨).
- 연관관계는 `LAZY` 명시, 컬렉션 매핑은 명세에 있을 때만.

## 3. Repository

- 위치: `{domain}/repository/`
- 목록 조회 메서드는 반드시 `Pageable`을 받는다.
- 원자적 갱신(카운터 등)은 `@Modifying(clearAutomatically = true)` + JPQL update.
- `ToOne` 함께 조회가 필요하면 `@EntityGraph` 또는 fetch join. 페이징 쿼리에서 컬렉션 fetch join 금지.

## 4. DTO

- 위치: `{domain}/dto/`
- 모두 `record`. 요청 DTO에 Bean Validation, 메시지는 한국어.
- 응답 DTO는 `from(Entity)` 정적 팩토리로 변환.
- 목록 응답은 `PageResponse.from(page)`.

## 5. 에러 코드

- 필요한 코드를 `ErrorCode`에 추가하고 `docs/error-codes.md` 표를 갱신한다.

## 6. Service

- 위치: `{domain}/service/`
- 클래스에 `@Transactional(readOnly = true)`, 쓰기 메서드에 `@Transactional`.
- 조회 실패는 `BusinessException(ErrorCode.XXX_NOT_FOUND)`.
- 엔티티를 Service 밖으로 반환하지 않는다 — 응답 DTO로 변환해서 반환.

## 7. Controller

- 위치: `{domain}/controller/`
- 경로/상태 코드는 `docs/api-spec.md`와 정확히 일치.
- 생성은 `ResponseEntity.created(location)`, 삭제는 `ResponseEntity.noContent()`.
- 요청 본문에 `@Valid`.

## 8. 테스트

`test-writer` 에이전트에 위임하거나 직접 작성한다. 최소 범위:

| 대상 | 방식 | 필수 케이스 |
|------|------|-------------|
| Repository | `@DataJpaTest` + Testcontainers | 커스텀 쿼리 전부, 페이징 |
| Service | Mockito | 정상, NOT_FOUND |
| Controller | `@WebMvcTest` + `MockMvcTester` + `@MockitoBean` | 정상 응답 형식, 검증 실패 400, 404 `ProblemDetail` |

## 9. 마무리

1. `./gradlew build` 실행 — 실패하면 고친 뒤 다시 실행. 통과 전에는 완료 보고하지 않는다.
2. `docs/roadmap.md`의 해당 항목을 `[x]`로 체크.
3. 명세 문서와 구현이 달라진 부분이 있으면 문서를 갱신하고 변경 내용을 사용자에게 보고.
4. 큰 기능이면 `code-reviewer` 에이전트로 리뷰를 받는다.
