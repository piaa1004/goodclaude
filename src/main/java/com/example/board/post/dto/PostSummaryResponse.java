package com.example.board.post.dto;

import com.example.board.post.domain.Post;
import java.time.Instant;

/**
 * 목록 항목. 본문(content)은 포함하지 않는다. commentCount는 Phase 2에서 추가한다.
 */
public record PostSummaryResponse(
        Long id,
        String title,
        String author,
        long viewCount,
        Instant createdAt
) {

    public static PostSummaryResponse from(Post post) {
        return new PostSummaryResponse(
                post.getId(),
                post.getTitle(),
                post.getAuthor(),
                post.getViewCount(),
                post.getCreatedAt()
        );
    }
}
