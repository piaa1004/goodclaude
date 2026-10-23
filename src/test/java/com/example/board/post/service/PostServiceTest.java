package com.example.board.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.board.global.common.PageResponse;
import com.example.board.global.error.BusinessException;
import com.example.board.global.error.ErrorCode;
import com.example.board.post.domain.Post;
import com.example.board.post.dto.PostCreateRequest;
import com.example.board.post.dto.PostDetailResponse;
import com.example.board.post.dto.PostSummaryResponse;
import com.example.board.post.dto.PostUpdateRequest;
import com.example.board.post.repository.PostRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T03:15:00Z");

    @Mock
    PostRepository postRepository;

    @InjectMocks
    PostService postService;

    private Post post(Long id, String title, String content, String author, long viewCount) {
        Post post = Post.create(title, content, author);
        ReflectionTestUtils.setField(post, "id", id);
        ReflectionTestUtils.setField(post, "viewCount", viewCount);
        ReflectionTestUtils.setField(post, "createdAt", NOW);
        ReflectionTestUtils.setField(post, "updatedAt", NOW);
        return post;
    }

    @Test
    @DisplayName("게시글을 작성하면 저장된 게시글을 조회수 0으로 반환한다")
    void createSavesPost() {
        // given
        PostCreateRequest request = new PostCreateRequest("첫 글입니다", "본문 내용", "intheeast");
        given(postRepository.save(any(Post.class))).willAnswer(invocation -> {
            Post saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });

        // when
        PostDetailResponse response = postService.create(request);

        // then
        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        Post saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("첫 글입니다");
        assertThat(saved.getContent()).isEqualTo("본문 내용");
        assertThat(saved.getAuthor()).isEqualTo("intheeast");
        assertThat(saved.getViewCount()).isZero();

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.title()).isEqualTo("첫 글입니다");
        assertThat(response.content()).isEqualTo("본문 내용");
        assertThat(response.author()).isEqualTo("intheeast");
        assertThat(response.viewCount()).isZero();
    }

    @Test
    @DisplayName("단건 조회는 조회수를 원자적으로 증가시킨 뒤 증가가 반영된 게시글을 반환한다")
    void getPostIncrementsViewCountThenFinds() {
        // given
        given(postRepository.incrementViewCount(1L)).willReturn(1);
        given(postRepository.findById(1L)).willReturn(Optional.of(post(1L, "제목", "본문", "author", 13L)));

        // when
        PostDetailResponse response = postService.getPost(1L);

        // then
        InOrder inOrder = inOrder(postRepository);
        inOrder.verify(postRepository).incrementViewCount(1L);
        inOrder.verify(postRepository).findById(1L);
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.viewCount()).isEqualTo(13L);
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.updatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("단건 조회 시 조회수 증가 행 수가 0이면 POST_NOT_FOUND 예외가 발생하고 조회하지 않는다")
    void getPostThrowsWhenIncrementReturnsZero() {
        // given
        given(postRepository.incrementViewCount(999L)).willReturn(0);

        // when / then
        assertThatThrownBy(() -> postService.getPost(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
        verify(postRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("목록 조회는 클라이언트 정렬을 무시하고 createdAt desc, id desc로 고정하며 page/size는 유지한다")
    void getPostsUsesFixedSortAndKeepsPaging() {
        // given
        Pageable requested = PageRequest.of(2, 5, Sort.by(Sort.Order.asc("title")));
        Post post = post(1L, "제목", "본문", "author", 3L);
        given(postRepository.findAll(any(Pageable.class)))
                .willAnswer(invocation -> new PageImpl<>(List.of(post), invocation.getArgument(0), 11));

        // when
        PageResponse<PostSummaryResponse> response = postService.getPosts(requested);

        // then
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).findAll(captor.capture());
        Pageable used = captor.getValue();
        assertThat(used.getPageNumber()).isEqualTo(2);
        assertThat(used.getPageSize()).isEqualTo(5);
        assertThat(used.getSort()).isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        assertThat(used.getSort().getOrderFor("title")).isNull();

        assertThat(response.content()).containsExactly(
                new PostSummaryResponse(1L, "제목", "author", 3L, NOW));
        assertThat(response.page().number()).isEqualTo(2);
        assertThat(response.page().size()).isEqualTo(5);
        assertThat(response.page().totalElements()).isEqualTo(11L);
        assertThat(response.page().totalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("게시글을 수정하면 제목과 본문이 바뀌고 flush 후 응답을 반환한다")
    void updateChangesTitleAndContent() {
        // given
        Post post = post(1L, "제목", "본문", "author", 5L);
        given(postRepository.findById(1L)).willReturn(Optional.of(post));
        PostUpdateRequest request = new PostUpdateRequest("수정된 제목", "수정된 본문");

        // when
        PostDetailResponse response = postService.update(1L, request);

        // then
        verify(postRepository).flush();
        assertThat(post.getTitle()).isEqualTo("수정된 제목");
        assertThat(post.getContent()).isEqualTo("수정된 본문");
        assertThat(response.title()).isEqualTo("수정된 제목");
        assertThat(response.content()).isEqualTo("수정된 본문");
        assertThat(response.author()).isEqualTo("author");
        assertThat(response.viewCount()).isEqualTo(5L);
    }

    @Test
    @DisplayName("존재하지 않는 게시글을 수정하면 POST_NOT_FOUND 예외가 발생한다")
    void updateThrowsWhenPostNotFound() {
        // given
        given(postRepository.findById(999L)).willReturn(Optional.empty());
        PostUpdateRequest request = new PostUpdateRequest("수정된 제목", "수정된 본문");

        // when / then
        assertThatThrownBy(() -> postService.update(999L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
        verify(postRepository, never()).flush();
    }

    @Test
    @DisplayName("게시글을 삭제하면 단일 delete 쿼리를 실행한다")
    void deleteRemovesPost() {
        // given
        given(postRepository.deleteByIdReturningCount(1L)).willReturn(1);

        // when
        postService.delete(1L);

        // then
        verify(postRepository).deleteByIdReturningCount(1L);
    }

    @Test
    @DisplayName("삭제된 행 수가 0이면 POST_NOT_FOUND 예외가 발생한다")
    void deleteThrowsWhenPostNotFound() {
        // given
        given(postRepository.deleteByIdReturningCount(999L)).willReturn(0);

        // when / then
        assertThatThrownBy(() -> postService.delete(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
    }
}
