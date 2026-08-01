package com.lia.core.pipeline.connector;

/** 국가법령정보 API가 오류를 반환했을 때(HTTP 200 + 오류 본문 포함). */
public class LawApiException extends RuntimeException {
    public LawApiException(String message) {
        super(message);
    }
}
