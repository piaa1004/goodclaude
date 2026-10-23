package com.example.board.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(controllers = ErrorTestController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvcTester mvc;

    private void assertProblem(MvcTestResult result, HttpStatus status, ErrorCode errorCode, String instance) {
        assertThat(result).hasStatus(status);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.status").asNumber().isEqualTo(status.value());
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo(status.getReasonPhrase());
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo(errorCode.getDetail());
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(errorCode.name());
        assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo(instance);
    }

    @Test
    @DisplayName("@Valid 요청 본문 검증 실패 시 400 INVALID_INPUT과 필드별 errors를 반환한다")
    void returnsInvalidInputWhenRequestBodyValidationFails() {
        // given
        String body = """
                {"title": "", "author": ""}
                """;

        // when
        MvcTestResult result = mvc.post().uri("/test/errors/body")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();

        // then
        assertProblem(result, HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT, "/test/errors/body");
        assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray()
                .containsExactlyInAnyOrder("title", "author");
        assertThat(result).bodyJson().extractingPath("$.errors[*].message").asArray()
                .containsExactlyInAnyOrder("제목은 필수입니다.", "작성자는 필수입니다.");
    }

    @Test
    @DisplayName("@RequestParam 메서드 파라미터 검증 실패 시 400 INVALID_INPUT과 errors를 반환한다")
    void returnsInvalidInputWhenMethodParameterValidationFails() {
        // given
        String keyword = "toolong";

        // when
        MvcTestResult result = mvc.get().uri("/test/errors/param")
                .param("keyword", keyword)
                .exchange();

        // then
        assertProblem(result, HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT, "/test/errors/param");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("keyword");
        assertThat(result).bodyJson().extractingPath("$.errors[0].message").isEqualTo("키워드는 3자 이하여야 합니다.");
    }

    @Test
    @DisplayName("잘못된 JSON 본문은 400 MALFORMED_JSON을 반환한다")
    void returnsMalformedJsonWhenBodyIsUnreadable() {
        // given
        String body = "{\"title\": \"abc\", ";

        // when
        MvcTestResult result = mvc.post().uri("/test/errors/body")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();

        // then
        assertProblem(result, HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_JSON, "/test/errors/body");
        assertThat(result).bodyJson().doesNotHavePath("$.errors");
    }

    @Test
    @DisplayName("PathVariable 타입이 맞지 않으면 400 INVALID_PARAMETER를 반환한다")
    void returnsInvalidParameterWhenPathVariableTypeMismatch() {
        // given
        String invalidId = "abc";

        // when
        MvcTestResult result = mvc.get().uri("/test/errors/items/{id}", invalidId).exchange();

        // then
        assertProblem(result, HttpStatus.BAD_REQUEST, ErrorCode.INVALID_PARAMETER, "/test/errors/items/abc");
    }

    @Test
    @DisplayName("필수 RequestParam이 누락되면 400 INVALID_PARAMETER를 반환한다")
    void returnsInvalidParameterWhenRequiredParamMissing() {
        // given / when
        MvcTestResult result = mvc.get().uri("/test/errors/required").exchange();

        // then
        assertProblem(result, HttpStatus.BAD_REQUEST, ErrorCode.INVALID_PARAMETER, "/test/errors/required");
    }

    @Test
    @DisplayName("BusinessException은 ErrorCode의 상태와 code로 응답한다")
    void returnsErrorCodeOfBusinessException() {
        // given / when
        MvcTestResult result = mvc.get().uri("/test/errors/business").exchange();

        // then
        assertProblem(result, HttpStatus.BAD_REQUEST, ErrorCode.INVALID_SORT_PROPERTY, "/test/errors/business");
    }

    @Test
    @DisplayName("매핑되지 않은 경로는 404 RESOURCE_NOT_FOUND를 반환한다")
    void returnsResourceNotFoundForUnmappedPath() {
        // given / when
        MvcTestResult result = mvc.get().uri("/test/errors/not-exists").exchange();

        // then
        assertProblem(result, HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "/test/errors/not-exists");
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드는 405 METHOD_NOT_ALLOWED를 반환한다")
    void returnsMethodNotAllowedForUnsupportedMethod() {
        // given / when
        MvcTestResult result = mvc.delete().uri("/test/errors/business").exchange();

        // then
        assertProblem(result, HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED, "/test/errors/business");
    }

    @Test
    @DisplayName("처리되지 않은 예외는 500 INTERNAL_ERROR와 고정 메시지를 반환하고 원본 메시지를 노출하지 않는다")
    void returnsInternalErrorWithoutLeakingOriginalMessage() {
        // given / when
        MvcTestResult result = mvc.get().uri("/test/errors/unexpected").exchange();

        // then
        assertProblem(result, HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "/test/errors/unexpected");
        assertThat(result).bodyText().doesNotContain("secret internal msg");
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type은 415 UNSUPPORTED_MEDIA_TYPE을 반환한다")
    void returnsUnsupportedMediaTypeForUnsupportedContentType() {
        // given / when
        MvcTestResult result = mvc.post().uri("/test/errors/body")
                .contentType(MediaType.TEXT_PLAIN)
                .content("title=abc")
                .exchange();

        // then
        assertProblem(result, HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE, "/test/errors/body");
    }

    @Test
    @DisplayName("Accept 헤더의 형식으로 응답할 수 없으면 406 NOT_ACCEPTABLE을 반환한다")
    void returnsNotAcceptableForUnsupportedAcceptHeader() {
        // given / when
        // Spring은 ProblemDetail에 한해 Accept와 무관하게 problem+json으로 본문을 쓴다 (프레임워크 기본 동작)
        MvcTestResult result = mvc.get().uri("/test/errors/json")
                .accept(MediaType.APPLICATION_XML)
                .exchange();

        // then
        assertProblem(result, HttpStatus.NOT_ACCEPTABLE, ErrorCode.NOT_ACCEPTABLE, "/test/errors/json");
    }

    @Test
    @DisplayName("서버 오류인 프레임워크 예외(MissingPathVariableException)는 400이 아닌 500 INTERNAL_ERROR를 반환한다")
    void returnsInternalErrorForServerSideFrameworkException() {
        // given / when
        MvcTestResult result = mvc.get().uri("/test/errors/mismatch/1").exchange();

        // then
        assertProblem(result, HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "/test/errors/mismatch/1");
    }
}
