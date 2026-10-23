package com.example.board.global.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GlobalExceptionHandler 검증용 테스트 전용 컨트롤러. 테스트 소스에만 존재한다.
 */
@RestController
@RequestMapping("/test/errors")
class ErrorTestController {

    record SampleRequest(
            @NotBlank(message = "제목은 필수입니다.")
            @Size(max = 10, message = "제목은 10자 이하여야 합니다.")
            String title,
            @NotBlank(message = "작성자는 필수입니다.")
            String author) {
    }

    @PostMapping("/body")
    String validateBody(@Valid @RequestBody SampleRequest request) {
        return "ok";
    }

    @GetMapping("/param")
    String validateParam(@RequestParam @Size(max = 3, message = "키워드는 3자 이하여야 합니다.") String keyword) {
        return keyword;
    }

    @GetMapping("/required")
    String requiredParam(@RequestParam String name) {
        return name;
    }

    @GetMapping("/items/{id}")
    String pathVariable(@PathVariable Long id) {
        return String.valueOf(id);
    }

    // 매핑 오류(서버 버그): 경로 변수 이름 불일치로 MissingPathVariableException(500) 발생
    @GetMapping("/mismatch/{id}")
    String missingPathVariable(@PathVariable("other") Long other) {
        return String.valueOf(other);
    }

    @GetMapping(value = "/json", produces = MediaType.APPLICATION_JSON_VALUE)
    SampleRequest jsonOnly() {
        return new SampleRequest("title", "author");
    }

    @GetMapping("/business")
    String business() {
        throw new BusinessException(ErrorCode.INVALID_SORT_PROPERTY);
    }

    @GetMapping("/unexpected")
    String unexpected() {
        throw new RuntimeException("secret internal msg");
    }
}
