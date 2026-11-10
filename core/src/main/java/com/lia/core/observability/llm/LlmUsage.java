package com.lia.core.observability.llm;

import java.util.Objects;

import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
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
        if (response == null) return NONE;
        Usage usage = present(response.getMetadata() == null ? null : response.getMetadata().getUsage());
        String stopReason = response.getResults().stream()
                .map(Generation::getMetadata)
                .filter(Objects::nonNull)
                .map(ChatGenerationMetadata::getFinishReason)
                .filter(reason -> reason != null && !reason.isBlank())
                .findFirst().orElse(null);
        return new LlmUsage(model(response.getMetadata() == null ? null : response.getMetadata().getModel()),
                usage == null ? null : usage.getPromptTokens(),
                usage == null ? null : usage.getCompletionTokens(),
                usage == null ? null : toInt(usage.getCacheReadInputTokens()),
                usage == null ? null : toInt(usage.getCacheWriteInputTokens()),
                stopReason);
    }

    /** 임베딩 응답 — 입력 토큰만 있다. */
    public static LlmUsage of(EmbeddingResponse response) {
        if (response == null || response.getMetadata() == null) return NONE;
        Usage usage = present(response.getMetadata().getUsage());
        return new LlmUsage(model(response.getMetadata().getModel()),
                usage == null ? null : usage.getPromptTokens(), null, null, null, null);
    }

    /** 공급자가 사용량을 주지 않으면 Spring AI 는 0을 내는 {@link EmptyUsage} 를 둔다 — 없는 것으로 본다. */
    private static Usage present(Usage usage) {
        return usage == null || usage instanceof EmptyUsage ? null : usage;
    }

    private static String model(String model) {
        return model == null || model.isBlank() ? null : model;
    }

    private static Integer toInt(Long tokens) {
        return tokens == null ? null : Math.toIntExact(tokens);
    }
}
