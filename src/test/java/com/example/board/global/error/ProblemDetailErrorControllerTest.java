package com.example.board.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(controllers = ProblemDetailErrorController.class)
class ProblemDetailErrorControllerTest {

    @Autowired
    MockMvcTester mvc;

    private void assertProblem(MvcTestResult result, HttpStatus status, ErrorCode errorCode, String instance) {
        assertThat(result).hasStatus(status);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo(errorCode.getDetail());
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(errorCode.name());
        assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo(instance);
    }

    @Test
    @DisplayName("/error 직접 호출은 404 RESOURCE_NOT_FOUND를 반환한다")
    void returnsResourceNotFoundForDirectAccess() {
        // given / when
        MvcTestResult result = mvc.get().uri("/error").exchange();

        // then
        assertProblem(result, HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "/error");
    }

    @Test
    @DisplayName("필터 등에서 발생한 예외의 에러 디스패치는 500 INTERNAL_ERROR를 반환하고 원본 메시지를 노출하지 않는다")
    void returnsInternalErrorForServerErrorDispatch() {
        // given / when
        MvcTestResult result = mvc.get().uri("/error")
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/posts")
                .requestAttr(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("secret internal msg"))
                .exchange();

        // then
        assertProblem(result, HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "/api/v1/posts");
        assertThat(result).bodyText().doesNotContain("secret internal msg");
    }

    @Test
    @DisplayName("원인이 하나로 정해지는 4xx 에러 디스패치는 해당 code로 응답한다")
    void returnsMappedCodeForClientErrorDispatch() {
        // given / when
        MvcTestResult result = mvc.get().uri("/error")
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 415)
                .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/posts")
                .exchange();

        // then
        assertProblem(result, HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE, "/api/v1/posts");
    }
}
