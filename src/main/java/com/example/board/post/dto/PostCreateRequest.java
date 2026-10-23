package com.example.board.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostCreateRequest(
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 100, message = "제목은 1~100자여야 합니다.")
        String title,

        @NotBlank(message = "본문은 필수입니다.")
        @Size(max = 10_000, message = "본문은 1~10,000자여야 합니다.")
        String content,

        @NotBlank(message = "작성자는 필수입니다.")
        @Size(max = 20, message = "작성자는 1~20자여야 합니다.")
        String author
) {
}
