package com.example.board.global.error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DispatcherServlet 밖에서 발생한 에러(필터 예외, sendError, /error 직접 호출)를 처리한다.
 * Boot 기본 BasicErrorController를 대체해 이 경로도 ProblemDetail + code로 응답한다.
 */
@Slf4j
@RestController
public class ProblemDetailErrorController implements ErrorController {

    @RequestMapping("${server.error.path:${error.path:/error}}")
    public ResponseEntity<ProblemDetail> handleError(HttpServletRequest request) {
        Integer statusCode = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        String requestUri = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);

        // 에러 디스패치가 아닌 /error 직접 호출은 존재하지 않는 리소스로 취급한다
        if (statusCode == null) {
            return respond(ErrorCode.RESOURCE_NOT_FOUND, request.getRequestURI());
        }

        HttpStatus status = HttpStatus.resolve(statusCode);
        Throwable exception = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        if (status == null || status.is5xxServerError()) {
            log.error("Error dispatch: status={}, uri={}", statusCode, requestUri, exception);
            return respond(ErrorCode.INTERNAL_ERROR, requestUri);
        }

        ErrorCode errorCode = resolveErrorCode(status);
        if (errorCode == null) {
            // code는 필수다. 여기 도달하면 ErrorCode를 추가하고 매핑해야 한다.
            log.error("ErrorCode mapping missing: status={}, uri={}", statusCode, requestUri);
            ProblemDetail problem = ProblemDetail.forStatus(status);
            problem.setInstance(instance(requestUri));
            return ResponseEntity.status(status).body(problem);
        }
        log.warn("Error dispatch: status={}, uri={}", statusCode, requestUri);
        return respond(errorCode, requestUri);
    }

    /**
     * 상태 코드만으로 원인이 하나로 정해지는 경우만 매핑한다. 400처럼 원인이 여러 가지인 상태는 일괄 매핑하지 않는다.
     */
    private @Nullable ErrorCode resolveErrorCode(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> ErrorCode.RESOURCE_NOT_FOUND;
            case METHOD_NOT_ALLOWED -> ErrorCode.METHOD_NOT_ALLOWED;
            case NOT_ACCEPTABLE -> ErrorCode.NOT_ACCEPTABLE;
            case UNSUPPORTED_MEDIA_TYPE -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> null;
        };
    }

    private ResponseEntity<ProblemDetail> respond(ErrorCode errorCode, @Nullable String requestUri) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.getStatus(), errorCode.getDetail());
        problem.setProperty("code", errorCode.code());
        problem.setInstance(instance(requestUri));
        return ResponseEntity.status(errorCode.getStatus()).body(problem);
    }

    private @Nullable URI instance(@Nullable String requestUri) {
        return requestUri == null ? null : URI.create(requestUri);
    }
}
