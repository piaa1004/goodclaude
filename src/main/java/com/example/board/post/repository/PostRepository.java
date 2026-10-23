package com.example.board.post.repository;

import com.example.board.post.domain.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * 조회수를 DB에서 원자적으로 1 증가시킨다. 동시 요청에서도 증가분이 유실되지 않는다.
     *
     * @return 갱신된 행 수 (0이면 게시글 없음)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Post p set p.viewCount = p.viewCount + 1 where p.id = :id")
    int incrementViewCount(@Param("id") Long id);

    /**
     * 게시글을 단일 delete 쿼리로 삭제한다. 댓글은 DB FK의 ON DELETE CASCADE로 함께 삭제된다.
     *
     * @return 삭제된 행 수 (0이면 게시글 없음)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Post p where p.id = :id")
    int deleteByIdReturningCount(@Param("id") Long id);
}
