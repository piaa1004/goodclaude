package com.example.board.post.controller;

import com.example.board.global.common.PageResponse;
import com.example.board.post.dto.PostCreateRequest;
import com.example.board.post.dto.PostDetailResponse;
import com.example.board.post.dto.PostSummaryResponse;
import com.example.board.post.dto.PostUpdateRequest;
import com.example.board.post.service.PostService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping
    public ResponseEntity<PostDetailResponse> create(@Valid @RequestBody PostCreateRequest request) {
        PostDetailResponse response = postService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/posts/" + response.id())).body(response);
    }

    @GetMapping
    public PageResponse<PostSummaryResponse> getPosts(@PageableDefault(size = 20) Pageable pageable) {
        return postService.getPosts(pageable);
    }

    @GetMapping("/{postId}")
    public PostDetailResponse getPost(@PathVariable Long postId) {
        return postService.getPost(postId);
    }

    @PutMapping("/{postId}")
    public PostDetailResponse update(@PathVariable Long postId, @Valid @RequestBody PostUpdateRequest request) {
        return postService.update(postId, request);
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> delete(@PathVariable Long postId) {
        postService.delete(postId);
        return ResponseEntity.noContent().build();
    }
}
