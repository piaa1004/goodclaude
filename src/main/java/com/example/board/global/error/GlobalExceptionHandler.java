package com.example.board.global.error;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 모든 에러를 ProblemDetail(RFC 9457) + code 확장 속성으로 응답한다. 형식은 docs/error-codes.md 참고.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String CODE = "code";
    private static final String ERRORS = "errors";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusinessException(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        log.warn("Business exception: code={}, message={}", errorCode.code(), ex.getMessage());
        return ResponseEntity.status(errorCode.getStatus()).body(problemDetail(errorCode));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpectedException(Exception ex) {
        log.error("Unexpected exception", ex);
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(errorCode.getStatus()).body(problemDetail(errorCode));
    }

    /**
     * ResponseEntityExceptionHandler가 처리하는 프레임워크 예외의 공통 진입점. 예외 종류에 맞는 code와 detail을 채운다.
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {

        ErrorCode errorCode = resolveErrorCode(ex, statusCode);
        List<FieldErrorDetail> errors = fieldErrors(ex);
        if (statusCode.is5xxServerError()) {
            log.error("Framework exception: status={}", statusCode.value(), ex);
        } else {
            // 예외 메시지에는 사용자 입력값(rejected value 등)이 섞이므로 타입과 필드명만 남긴다.
            log.warn("Framework exception: status={}, type={}, fields={}",
                    statusCode.value(), ex.getClass().getSimpleName(),
                    errors.stream().map(FieldErrorDetail::field).toList());
        }

        if (errorCode == null) {
            // code는 필수다. 여기 도달하면 ErrorCode를 추가하고 resolveErrorCode에 매핑해야 한다.
            log.error("ErrorCode mapping missing: status={}, type={}", statusCode.value(), ex.getClass().getName());
            return super.handleExceptionInternal(ex, body, headers, statusCode, request);
        }

        ProblemDetail problem = problemDetail(errorCode);
        if (!errors.isEmpty()) {
            problem.setProperty(ERRORS, errors);
        }
        return super.handleExceptionInternal(ex, problem, headers, errorCode.getStatus(), request);
    }

    private @Nullable ErrorCode resolveErrorCode(Exception ex, HttpStatusCode statusCode) {
        // ConversionNotSupportedException(TypeMismatchException 하위), MissingPathVariableException
        // (ServletRequestBindingException 하위)은 서버 오류이므로 instanceof 분기보다 먼저 판별한다.
        if (statusCode.is5xxServerError()) {
            return ErrorCode.INTERNAL_ERROR;
        }
        if (ex instanceof MethodArgumentNotValidException || ex instanceof HandlerMethodValidationException) {
            return ErrorCode.INVALID_INPUT;
        }
        if (ex instanceof HttpMessageNotReadableException) {
            return ErrorCode.MALFORMED_JSON;
        }
        // TypeMismatchException: MethodArgumentTypeMismatchException 포함
        // ServletRequestBindingException: 필수 파라미터/헤더/쿠키 누락(MissingServletRequestParameterException 등)
        if (ex instanceof TypeMismatchException
                || ex instanceof ServletRequestBindingException
                || ex instanceof MissingServletRequestPartException) {
            return ErrorCode.INVALID_PARAMETER;
        }
        if (ex instanceof NoResourceFoundException || ex instanceof NoHandlerFoundException) {
            return ErrorCode.RESOURCE_NOT_FOUND;
        }
        if (ex instanceof HttpRequestMethodNotSupportedException) {
            return ErrorCode.METHOD_NOT_ALLOWED;
        }
        if (ex instanceof HttpMediaTypeNotSupportedException) {
            return ErrorCode.UNSUPPORTED_MEDIA_TYPE;
        }
        if (ex instanceof HttpMediaTypeNotAcceptableException) {
            return ErrorCode.NOT_ACCEPTABLE;
        }
        return null;
    }

    private List<FieldErrorDetail> fieldErrors(Exception ex) {
        if (ex instanceof MethodArgumentNotValidException e) {
            return e.getBindingResult().getFieldErrors().stream()
                    .map(fe -> new FieldErrorDetail(fe.getField(), fe.getDefaultMessage()))
                    .toList();
        }
        if (ex instanceof HandlerMethodValidationException e) {
            return e.getParameterValidationResults().stream()
                    .flatMap(result -> result.getResolvableErrors().stream()
                            .map(error -> new FieldErrorDetail(
                                    fieldName(error, result.getMethodParameter().getParameterName()),
                                    error.getDefaultMessage())))
                    .toList();
        }
        return List.of();
    }

    private String fieldName(MessageSourceResolvable error, @Nullable String parameterName) {
        if (error instanceof FieldError fieldError) {
            return fieldError.getField();
        }
        return parameterName;
    }

    private ProblemDetail problemDetail(ErrorCode errorCode) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.getStatus(), errorCode.getDetail());
        problem.setProperty(CODE, errorCode.code());
        return problem;
    }
}
