package com.example.board.post.domain;

import com.example.board.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false, length = 20, updatable = false)
    private String author;

    // 증가는 PostRepository.incrementViewCount(원자적 update)로만 한다
    @Column(nullable = false)
    private long viewCount;

    private Post(String title, String content, String author) {
        this.title = title;
        this.content = content;
        this.author = author;
        this.viewCount = 0L;
    }

    public static Post create(String title, String content, String author) {
        return new Post(title, content, author);
    }

    public void update(String title, String content) {
        this.title = title;
        this.content = content;
    }
}
