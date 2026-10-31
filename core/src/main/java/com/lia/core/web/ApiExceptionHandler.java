package com.lia.core.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.lia.core.application.account.AccountNotFoundException;
import com.lia.core.application.bookmark.BookmarkLimitException;
import com.lia.core.application.bookmark.UnknownLawException;
import com.lia.core.application.history.AnalysisExpiredException;
import com.lia.core.application.history.HistoryLimitException;
import com.lia.core.application.history.HistoryNotFoundException;
import com.lia.core.application.profile.ConsentRequiredException;
import com.lia.core.application.subscription.SubscriptionLimitException;

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

    /** 쿼리·경로 파라미터 형식 오류(예: limit=abc) — 원문 값은 싣지 않고 파라미터 이름만 알린다. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> parameterTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "bad_request", "message", e.getName() + " 값이 올바르지 않습니다."));
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

    /** 요청한 법령·버전 없음 — 404. */
    @ExceptionHandler(LawNotFoundException.class)
    public ResponseEntity<Map<String, Object>> lawNotFound(LawNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "not_found", "message", e.getMessage()));
    }

    /** 북마크할 법령이 저장소에 없음 — 404(D72). */
    @ExceptionHandler(UnknownLawException.class)
    public ResponseEntity<Map<String, Object>> unknownLaw(UnknownLawException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "not_found", "message", e.getMessage()));
    }

    /** 사용자당 북마크 한도 초과 — 409(D72 ④). 메시지(한도 숫자 포함)는 화면에 그대로 나간다. */
    @ExceptionHandler(BookmarkLimitException.class)
    public ResponseEntity<Map<String, Object>> bookmarkLimit(BookmarkLimitException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "bookmark_limit", "message", e.getMessage()));
    }

    /** 저장할 분석 결과가 세션에 없음 — 409(D73). 메시지는 화면에 그대로. */
    @ExceptionHandler(AnalysisExpiredException.class)
    public ResponseEntity<Map<String, Object>> analysisExpired(AnalysisExpiredException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "analysis_expired", "message", e.getMessage()));
    }

    /** 사용자당 이력 한도 초과 — 409(D73 ⑥). */
    @ExceptionHandler(HistoryLimitException.class)
    public ResponseEntity<Map<String, Object>> historyLimit(HistoryLimitException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "history_limit", "message", e.getMessage()));
    }

    /** 이력이 없거나 남의 기록 — 404(존재를 숨긴다). */
    @ExceptionHandler(HistoryNotFoundException.class)
    public ResponseEntity<Map<String, Object>> historyNotFound(HistoryNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "not_found", "message", e.getMessage()));
    }

    /** 사용자당 구독 한도 초과 — 409(D74 ⑥). 메시지는 화면에 그대로. */
    @ExceptionHandler(SubscriptionLimitException.class)
    public ResponseEntity<Map<String, Object>> subscriptionLimit(SubscriptionLimitException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "subscription_limit", "message", e.getMessage()));
    }
}
