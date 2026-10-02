package com.lia.core.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.lia.core.application.account.AccountNotFoundException;
import com.lia.core.application.profile.ConsentRequiredException;

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

    /** 현재 처리방침 버전의 프로필 동의 없이 수정 — 409(403 은 CSRF 실패와 구분되지 않아 쓰지 않는다, D61). */
    @ExceptionHandler(ConsentRequiredException.class)
    public ResponseEntity<Map<String, Object>> consentRequired(ConsentRequiredException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "consent_required", "message", e.getMessage()));
    }

    /** 세션이 가리키는 계정이 이미 없음(다른 기기의 계정 삭제 등) — 401(service-api-spec §4.1). */
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<Map<String, Object>> accountNotFound(AccountNotFoundException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "unauthenticated", "message", e.getMessage()));
    }
}
