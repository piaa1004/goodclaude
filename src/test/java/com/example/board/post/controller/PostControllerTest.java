package com.example.board.post.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.board.global.common.PageResponse;
import com.example.board.global.error.BusinessException;
import com.example.board.global.error.ErrorCode;
import com.example.board.post.dto.PostCreateRequest;
import com.example.board.post.dto.PostDetailResponse;
import com.example.board.post.dto.PostSummaryResponse;
import com.example.board.post.dto.PostUpdateRequest;
import com.example.board.post.service.PostService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(PostController.class)
class PostControllerTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-23T03:15:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-10-23T04:20:30.123456Z");

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    PostService postService;

    private PostDetailResponse detail(Long id, String title, String content, long viewCount, Instant updatedAt) {
        return new PostDetailResponse(id, title, content, "intheeast", viewCount, CREATED_AT, updatedAt);
    }

    private MvcTestResult postJson(String body) {
        return mvc.post().uri("/api/v1/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult putJson(Long postId, String body) {
        return mvc.put().uri("/api/v1/posts/{postId}", postId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static String createBody(String title, String content, String author) {
        return """
                {"title": "%s", "content": "%s", "author": "%s"}
                """.formatted(title, content, author);
    }

    private void assertProblem(MvcTestResult result, HttpStatus status, ErrorCode errorCode, String instance) {
        assertThat(result).hasStatus(status);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.status").asNumber().isEqualTo(status.value());
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo(errorCode.getDetail());
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(errorCode.name());
        assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo(instance);
    }

    private void assertInvalidInput(MvcTestResult result, String instance, String... fields) {
        assertProblem(result, HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT, instance);
        assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray()
                .containsExactlyInAnyOrder((Object[]) fields);
    }

    // ---- 작성 ----

    @Test
    @DisplayName("게시글 작성 시 201과 Location 헤더, 작성된 게시글을 반환한다")
    void createReturnsCreated() {
        // given
        given(postService.create(any(PostCreateRequest.class)))
                .willReturn(detail(1L, "첫 글입니다", "본문 내용", 0L, CREATED_AT));

        // when
        MvcTestResult result = postJson(createBody("첫 글입니다", "본문 내용", "intheeast"));

        // then
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).headers().hasValue("Location", "/api/v1/posts/1");
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {
                  "id": 1,
                  "title": "첫 글입니다",
                  "content": "본문 내용",
                  "author": "intheeast",
                  "viewCount": 0,
                  "createdAt": "2026-10-23T03:15:00Z",
                  "updatedAt": "2026-10-23T03:15:00Z"
                }
                """);
        verify(postService).create(new PostCreateRequest("첫 글입니다", "본문 내용", "intheeast"));
    }

    @Test
    @DisplayName("필드 길이 상한(제목 100자, 본문 10,000자, 작성자 20자)은 허용한다")
    void createAcceptsMaxLengthFields() {
        // given
        given(postService.create(any(PostCreateRequest.class)))
                .willReturn(detail(1L, "t", "c", 0L, CREATED_AT));

        // when
        MvcTestResult result = postJson(createBody("가".repeat(100), "a".repeat(10_000), "w".repeat(20)));

        // then
        assertThat(result).hasStatus(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("제목이 공백이면 400 INVALID_INPUT과 title 필드 에러를 반환한다")
    void createRejectsBlankTitle() {
        // given / when
        MvcTestResult result = postJson(createBody("   ", "본문", "intheeast"));

        // then
        assertInvalidInput(result, "/api/v1/posts", "title");
        verify(postService, never()).create(any());
    }

    @Test
    @DisplayName("제목이 101자이면 400 INVALID_INPUT과 title 필드 에러를 반환한다")
    void createRejectsTooLongTitle() {
        // given / when
        MvcTestResult result = postJson(createBody("가".repeat(101), "본문", "intheeast"));

        // then
        assertInvalidInput(result, "/api/v1/posts", "title");
        assertThat(result).bodyJson().extractingPath("$.errors[0].message").isEqualTo("제목은 1~100자여야 합니다.");
    }

    @Test
    @DisplayName("본문이 10,001자이면 400 INVALID_INPUT과 content 필드 에러를 반환한다")
    void createRejectsTooLongContent() {
        // given / when
        MvcTestResult result = postJson(createBody("제목", "a".repeat(10_001), "intheeast"));

        // then
        assertInvalidInput(result, "/api/v1/posts", "content");
    }

    @Test
    @DisplayName("본문이 공백이면 400 INVALID_INPUT과 content 필드 에러를 반환한다")
    void createRejectsBlankContent() {
        // given / when
        MvcTestResult result = postJson(createBody("제목", " ", "intheeast"));

        // then
        assertInvalidInput(result, "/api/v1/posts", "content");
    }

    @Test
    @DisplayName("작성자가 21자이면 400 INVALID_INPUT과 author 필드 에러를 반환한다")
    void createRejectsTooLongAuthor() {
        // given / when
        MvcTestResult result = postJson(createBody("제목", "본문", "w".repeat(21)));

        // then
        assertInvalidInput(result, "/api/v1/posts", "author");
    }

    @Test
    @DisplayName("필수 필드가 모두 누락되면 400 INVALID_INPUT과 title, content, author 필드 에러를 반환한다")
    void createRejectsMissingFields() {
        // given / when
        MvcTestResult result = postJson("{}");

        // then
        assertInvalidInput(result, "/api/v1/posts", "title", "content", "author");
    }

    // ---- 단건 조회 ----

    @Test
    @DisplayName("게시글 단건 조회 시 200과 게시글 상세를 반환한다")
    void getPostReturnsDetail() {
        // given
        given(postService.getPost(1L)).willReturn(detail(1L, "제목", "본문", 13L, UPDATED_AT));

        // when
        MvcTestResult result = mvc.get().uri("/api/v1/posts/{postId}", 1L).exchange();

        // then
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {
                  "id": 1,
                  "title": "제목",
                  "content": "본문",
                  "author": "intheeast",
                  "viewCount": 13,
                  "createdAt": "2026-10-23T03:15:00Z",
                  "updatedAt": "2026-10-23T04:20:30.123456Z"
                }
                """);
    }

    @Test
    @DisplayName("시각 필드는 ISO-8601 UTC 문자열(Z)로 직렬화된다")
    void serializesInstantAsIsoUtcString() {
        // given
        given(postService.getPost(1L)).willReturn(detail(1L, "제목", "본문", 0L, UPDATED_AT));

        // when
        MvcTestResult result = mvc.get().uri("/api/v1/posts/{postId}", 1L).exchange();

        // then
        assertThat(result).bodyJson().extractingPath("$.createdAt").asString()
                .isEqualTo("2026-10-23T03:15:00Z")
                .endsWith("Z");
        assertThat(result).bodyJson().extractingPath("$.updatedAt").asString()
                .isEqualTo("2026-10-23T04:20:30.123456Z");
    }

    @Test
    @DisplayName("존재하지 않는 게시글을 조회하면 404 POST_NOT_FOUND ProblemDetail을 반환한다")
    void getPostReturnsNotFound() {
        // given
        given(postService.getPost(999L)).willThrow(new BusinessException(ErrorCode.POST_NOT_FOUND));

        // when
        MvcTestResult result = mvc.get().uri("/api/v1/posts/{postId}", 999L).exchange();

        // then
        assertProblem(result, HttpStatus.NOT_FOUND, ErrorCode.POST_NOT_FOUND, "/api/v1/posts/999");
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Not Found");
    }

    @Test
    @DisplayName("postId가 숫자가 아니면 400 INVALID_PARAMETER를 반환한다")
    void getPostReturnsInvalidParameterWhenPostIdTypeMismatch() {
        // given / when
        MvcTestResult result = mvc.get().uri("/api/v1/posts/{postId}", "abc").exchange();

        // then
        assertProblem(result, HttpStatus.BAD_REQUEST, ErrorCode.INVALID_PARAMETER, "/api/v1/posts/abc");
        verify(postService, never()).getPost(anyLong());
    }

    // ---- 목록 ----

    @Test
    @DisplayName("게시글 목록 조회 시 200과 PageResponse 형식(content, page)을 반환한다")
    void getPostsReturnsPageResponse() {
        // given
        PostSummaryResponse summary = new PostSummaryResponse(1L, "첫 글입니다", "intheeast", 12L, CREATED_AT);
        given(postService.getPosts(any(Pageable.class))).willReturn(
                new PageResponse<>(List.of(summary), new PageResponse.PageInfo(0, 20, 1L, 1)));

        // when
        MvcTestResult result = mvc.get().uri("/api/v1/posts").exchange();

        // then
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {
                  "content": [
                    {
                      "id": 1,
                      "title": "첫 글입니다",
                      "author": "intheeast",
                      "viewCount": 12,
                      "createdAt": "2026-10-23T03:15:00Z"
                    }
                  ],
                  "page": { "number": 0, "size": 20, "totalElements": 1, "totalPages": 1 }
                }
                """);
        assertThat(result).bodyJson().doesNotHavePath("$.content[0].content");
    }

    @Test
    @DisplayName("목록 조회 시 page/size를 생략하면 page=0, size=20으로 Service에 전달된다")
    void getPostsUsesDefaultPaging() {
        // given
        given(postService.getPosts(any(Pageable.class))).willReturn(
                new PageResponse<>(List.of(), new PageResponse.PageInfo(0, 20, 0L, 0)));

        // when
        MvcTestResult result = mvc.get().uri("/api/v1/posts").exchange();

        // then
        assertThat(result).hasStatusOk();
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(postService).getPosts(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("목록 조회 시 size가 100을 초과하면 100으로 보정되어 Service에 전달된다")
    void getPostsCapsPageSizeAtHundred() {
        // given
        given(postService.getPosts(any(Pageable.class))).willReturn(
                new PageResponse<>(List.of(), new PageResponse.PageInfo(1, 100, 0L, 0)));

        // when
        MvcTestResult result = mvc.get().uri("/api/v1/posts")
                .param("page", "1")
                .param("size", "500")
                .exchange();

        // then
        assertThat(result).hasStatusOk();
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(postService).getPosts(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    // ---- 수정 ----

    @Test
    @DisplayName("게시글 수정 시 200과 수정된 게시글을 반환한다")
    void updateReturnsUpdatedPost() {
        // given
        given(postService.update(1L, new PostUpdateRequest("수정된 제목", "수정된 본문")))
                .willReturn(detail(1L, "수정된 제목", "수정된 본문", 3L, UPDATED_AT));

        // when
        MvcTestResult result = putJson(1L, """
                {"title": "수정된 제목", "content": "수정된 본문"}
                """);

        // then
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.id").asNumber().isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("수정된 제목");
        assertThat(result).bodyJson().extractingPath("$.content").isEqualTo("수정된 본문");
        assertThat(result).bodyJson().extractingPath("$.author").isEqualTo("intheeast");
        assertThat(result).bodyJson().extractingPath("$.updatedAt").isEqualTo("2026-10-23T04:20:30.123456Z");
    }

    @Test
    @DisplayName("수정 요청의 제목/본문이 누락되면 400 INVALID_INPUT과 필드 에러를 반환한다")
    void updateRejectsMissingFields() {
        // given / when
        MvcTestResult result = putJson(1L, "{}");

        // then
        assertInvalidInput(result, "/api/v1/posts/1", "title", "content");
        verify(postService, never()).update(anyLong(), any());
    }

    @Test
    @DisplayName("수정 요청의 제목이 101자이면 400 INVALID_INPUT을 반환한다")
    void updateRejectsTooLongTitle() {
        // given / when
        MvcTestResult result = putJson(1L, """
                {"title": "%s", "content": "본문"}
                """.formatted("가".repeat(101)));

        // then
        assertInvalidInput(result, "/api/v1/posts/1", "title");
    }

    @Test
    @DisplayName("존재하지 않는 게시글을 수정하면 404 POST_NOT_FOUND를 반환한다")
    void updateReturnsNotFound() {
        // given
        given(postService.update(anyLong(), any(PostUpdateRequest.class)))
                .willThrow(new BusinessException(ErrorCode.POST_NOT_FOUND));

        // when
        MvcTestResult result = putJson(999L, """
                {"title": "수정된 제목", "content": "수정된 본문"}
                """);

        // then
        assertProblem(result, HttpStatus.NOT_FOUND, ErrorCode.POST_NOT_FOUND, "/api/v1/posts/999");
    }

    // ---- 삭제 ----

    @Test
    @DisplayName("게시글 삭제 시 204와 빈 본문을 반환한다")
    void deleteReturnsNoContent() {
        // given / when
        MvcTestResult result = mvc.delete().uri("/api/v1/posts/{postId}", 1L).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(result).body().isEmpty();
        verify(postService).delete(1L);
    }

    @Test
    @DisplayName("존재하지 않는 게시글을 삭제하면 404 POST_NOT_FOUND를 반환한다")
    void deleteReturnsNotFound() {
        // given
        willThrow(new BusinessException(ErrorCode.POST_NOT_FOUND)).given(postService).delete(999L);

        // when
        MvcTestResult result = mvc.delete().uri("/api/v1/posts/{postId}", 999L).exchange();

        // then
        assertProblem(result, HttpStatus.NOT_FOUND, ErrorCode.POST_NOT_FOUND, "/api/v1/posts/999");
    }

    @Test
    @DisplayName("삭제 시 postId가 숫자가 아니면 400 INVALID_PARAMETER를 반환한다")
    void deleteReturnsInvalidParameterWhenPostIdTypeMismatch() {
        // given / when
        MvcTestResult result = mvc.delete().uri("/api/v1/posts/{postId}", "abc").exchange();

        // then
        assertProblem(result, HttpStatus.BAD_REQUEST, ErrorCode.INVALID_PARAMETER, "/api/v1/posts/abc");
        verify(postService, never()).delete(anyLong());
    }
}
