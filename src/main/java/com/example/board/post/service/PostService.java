package com.example.board.post.service;

import com.example.board.global.common.PageResponse;
import com.example.board.global.error.BusinessException;
import com.example.board.global.error.ErrorCode;
import com.example.board.post.domain.Post;
import com.example.board.post.dto.PostCreateRequest;
import com.example.board.post.dto.PostDetailResponse;
import com.example.board.post.dto.PostSummaryResponse;
import com.example.board.post.dto.PostUpdateRequest;
import com.example.board.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    // 정렬 화이트리스트(Phase 3) 전까지 클라이언트 sort는 쿼리에 넘기지 않고 기본 정렬로 고정한다
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final PostRepository postRepository;

    @Transactional
    public PostDetailResponse create(PostCreateRequest request) {
        Post post = postRepository.save(Post.create(request.title(), request.content(), request.author()));
        return PostDetailResponse.from(post);
    }

    /**
     * 조회수를 원자적으로 증가시킨 뒤, 증가가 반영된 값을 조회해 반환한다.
     */
    @Transactional
    public PostDetailResponse getPost(Long postId) {
        if (postRepository.incrementViewCount(postId) == 0) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        return PostDetailResponse.from(findPost(postId));
    }

    public PageResponse<PostSummaryResponse> getPosts(Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_SORT);
        return PageResponse.from(postRepository.findAll(sorted).map(PostSummaryResponse::from));
    }

    @Transactional
    public PostDetailResponse update(Long postId, PostUpdateRequest request) {
        Post post = findPost(postId);
        post.update(request.title(), request.content());
        // updatedAt(@LastModifiedDate)은 flush 시점에 갱신되므로 응답 변환 전에 flush한다
        postRepository.flush();
        return PostDetailResponse.from(post);
    }

    @Transactional
    public void delete(Long postId) {
        if (postRepository.deleteByIdReturningCount(postId) == 0) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
    }

    private Post findPost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }
}
