package com.example.board.global.error;

/**
 * 검증 실패 시 ProblemDetail의 errors 확장 속성 항목.
 */
public record FieldErrorDetail(String field, String message) {
}
