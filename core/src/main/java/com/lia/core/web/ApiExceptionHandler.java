package com.lia.core.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * API 오류 매핑([[service-api-spec]] §4.1 — <b>시스템 오류만 4xx/5xx</b>).
 * 잘못된 요청(빈 query 등)은 400. 해소 실패·근거 부족은 예외가 아니라 200 본문(resolution/unmet)이다.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "bad_request", "message", e.getMessage()));
    }
}
