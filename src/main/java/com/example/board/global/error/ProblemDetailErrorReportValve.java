package com.example.board.global.error;

import java.io.PrintWriter;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ErrorReportValve;
import org.apache.coyote.ActionCode;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * 애플리케이션(DispatcherServlet)에 도달하기 전에 Tomcat이 거부한 요청(잘못된 URL 인코딩 등)의 에러 응답.
 * Tomcat 기본 HTML 에러 페이지 대신 ProblemDetail + code로 응답한다.
 * Spring이 이미 본문을 쓴 응답에는 관여하지 않는다.
 */
@Slf4j
public class ProblemDetailErrorReportValve extends ErrorReportValve {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Override
    protected void report(Request request, Response response, Throwable throwable) {
        int statusCode = response.getStatus();
        if (statusCode < 400 || response.getContentWritten() > 0 || !response.setErrorReported()) {
            return;
        }
        // Tomcat 기본 ErrorReportValve와 동일하게, 연결 상태상 I/O가 불가능하면 본문을 쓰지 않는다
        AtomicBoolean ioAllowed = new AtomicBoolean(true);
        response.getCoyoteResponse().action(ActionCode.IS_IO_ALLOWED, ioAllowed);
        if (!ioAllowed.get()) {
            return;
        }

        HttpStatus status = HttpStatus.resolve(statusCode);
        ErrorCode errorCode = resolveErrorCode(status);
        if (errorCode == null) {
            // code는 필수다. 여기 도달하면 ErrorCode를 추가하고 매핑해야 한다.
            log.error("ErrorCode mapping missing (container): status={}", statusCode);
        } else if (errorCode == ErrorCode.INTERNAL_ERROR) {
            log.error("Container error: status={}", statusCode, throwable);
        } else {
            log.warn("Container rejected request: status={}, code={}", statusCode, errorCode.code());
        }

        try {
            // 503, 505 등도 INTERNAL_ERROR(500)로 응답하므로 상태줄과 본문 status를 일치시킨다 (ProblemDetailErrorController와 동일)
            if (errorCode != null) {
                response.setStatus(errorCode.getStatus().value());
            }
            // writer를 얻기 전에 문자셋을 정해야 한글 detail이 깨지지 않는다
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            PrintWriter writer = response.getReporter();
            if (writer == null) {
                return;
            }
            writer.write(JSON_MAPPER.writeValueAsString(body(statusCode, status, errorCode, request.getRequestURI())));
            response.finishResponse();
        } catch (Exception ex) {
            log.warn("Failed to write container error response: status={}", statusCode, ex);
        }
    }

    /**
     * 컨테이너 단계 400은 요청 자체(요청 라인, URL 인코딩 등)가 잘못된 경우다.
     */
    private @Nullable ErrorCode resolveErrorCode(@Nullable HttpStatus status) {
        if (status == null || status.is5xxServerError()) {
            return ErrorCode.INTERNAL_ERROR;
        }
        return switch (status) {
            case BAD_REQUEST -> ErrorCode.MALFORMED_REQUEST;
            case NOT_FOUND -> ErrorCode.RESOURCE_NOT_FOUND;
            case METHOD_NOT_ALLOWED -> ErrorCode.METHOD_NOT_ALLOWED;
            case NOT_ACCEPTABLE -> ErrorCode.NOT_ACCEPTABLE;
            case UNSUPPORTED_MEDIA_TYPE -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> null;
        };
    }

    private Map<String, Object> body(int statusCode, @Nullable HttpStatus status, @Nullable ErrorCode errorCode,
                                     @Nullable String requestUri) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (errorCode != null) {
            body.put("title", errorCode.getStatus().getReasonPhrase());
            body.put("status", errorCode.getStatus().value());
            body.put("detail", errorCode.getDetail());
        } else {
            body.put("title", status != null ? status.getReasonPhrase() : "Error");
            body.put("status", statusCode);
        }
        String instance = validInstance(requestUri);
        if (instance != null) {
            body.put("instance", instance);
        }
        if (errorCode != null) {
            body.put("code", errorCode.code());
        }
        return body;
    }

    // 거부 사유가 URI 자체인 경우가 많으므로, URI로 해석 가능한 경우에만 instance에 담는다
    private @Nullable String validInstance(@Nullable String requestUri) {
        if (requestUri == null) {
            return null;
        }
        try {
            URI.create(requestUri);
            return requestUri;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
