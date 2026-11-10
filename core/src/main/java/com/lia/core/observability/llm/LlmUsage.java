package com.lia.core.observability.llm;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * 응답 1건이 알려 준 모델·토큰·종료 사유. 공급자가 주지 않은 값은 null — 0으로 꾸미지 않는다.
 *
 * @param inputTokens  공급자의 입력 토큰(Anthropic 은 캐시 읽기·쓰기를 뺀 값)
 * @param outputTokens thinking 포함 출력 토큰
 */
public record LlmUsage(String model, Integer inputTokens, Integer outputTokens,
                       Integer cacheReadTokens, Integer cacheWriteTokens, String stopReason) {

    /** 응답이 없었다(호출 실패·가짜 추론기). */
    public static final LlmUsage NONE = new LlmUsage(null, null, null, null, null, null);

    /** 채팅 응답 — 메타데이터의 모델·사용량, 종료 사유는 종료 사유가 있는 첫 generation. */
    public static LlmUsage of(ChatResponse response) {
        return NONE;
    }

    /** 임베딩 응답 — 입력 토큰만 있다. */
    public static LlmUsage of(EmbeddingResponse response) {
        return NONE;
    }
}
