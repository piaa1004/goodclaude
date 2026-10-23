package com.example.board.post.dto;

import com.example.board.post.domain.Post;
import java.time.Instant;

public record PostDetailResponse(
        Long id,
        String title,
        String content,
        String author,
        long viewCount,
        Instant createdAt,
        Instant updatedAt
) {

    public static PostDetailResponse from(Post post) {
        return new PostDetailResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getAuthor(),
                post.getViewCount(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
