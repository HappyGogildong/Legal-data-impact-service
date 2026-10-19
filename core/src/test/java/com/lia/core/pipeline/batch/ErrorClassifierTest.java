package com.lia.core.pipeline.batch;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import com.lia.core.pipeline.connector.LawApiException;

/** 오류 분류 — 영구(즉시 격리) vs 일시적(다음 실행 재시도). */
class ErrorClassifierTest {

    @Test
    @DisplayName("영구 — API 논리 오류·입력/파싱 오류·제약 위반·429 외 4xx")
    void 영구() {
        assertTrue(ErrorClassifier.isPermanent(new LawApiException("잘못된 MST")));
        assertTrue(ErrorClassifier.isPermanent(new IllegalArgumentException("법령ID는 필수")));
        assertTrue(ErrorClassifier.isPermanent(new DataIntegrityViolationException("제약 위반")));
        assertTrue(ErrorClassifier.isPermanent(
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "nf", HttpHeaders.EMPTY, null, null)));
        assertTrue(ErrorClassifier.isPermanent(new RuntimeException("감쌈", new IllegalArgumentException("원인"))),
                "원인 체인까지 본다");
    }

    @Test
    @DisplayName("일시적 — 네트워크·타임아웃·429·그 밖의 예외")
    void 일시적() {
        assertFalse(ErrorClassifier.isPermanent(new ResourceAccessException("timeout", new IOException("read timed out"))));
        assertFalse(ErrorClassifier.isPermanent(
                HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "slow down", HttpHeaders.EMPTY, null, null)));
        assertFalse(ErrorClassifier.isPermanent(new IllegalStateException("모르는 오류")));
    }
}
