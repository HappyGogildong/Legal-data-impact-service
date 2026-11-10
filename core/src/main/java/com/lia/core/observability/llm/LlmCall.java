package com.lia.core.observability.llm;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * {@code llm_calls} 한 행. 사람을 식별하거나 내용을 되살릴 값은 담지 않는다.
 *
 * @param model           응답이 알려 준 모델, 없으면 요청한 모델, 그것도 없으면 {@code unknown}
 * @param promptVersion   분석 프롬프트 버전(분류·임베딩은 null)
 * @param groupKey        분석 1회의 재시도 묶음(분류·임베딩은 null)
 * @param attempt         묶음 안 시도 번호(1부터)
 * @param accepted        인용 검증을 통과해 쓰였나(판정 없음·호출 실패는 null)
 * @param succeeded       예외 없이 결과를 냈나
 * @param requested       첫 Layer B 에서 나갈 차원(예: {@code IMPACT,ACTION}), 그 밖엔 null
 * @param contextSources  주입한 근거 블록 수(분석만)
 */
public record LlmCall(Instant calledAt, LlmPurpose purpose, String model, String promptVersion, UUID groupKey,
                      int attempt, Boolean accepted, boolean succeeded, String requested, LlmUsage usage,
                      Integer contextSources) {

    static final String UNKNOWN_MODEL = "unknown";

    public LlmCall {
        Objects.requireNonNull(calledAt, "calledAt");
        Objects.requireNonNull(purpose, "purpose");
        usage = usage == null ? LlmUsage.NONE : usage;
        model = model != null ? model : usage.model() != null ? usage.model() : UNKNOWN_MODEL;
        if (attempt < 1) throw new IllegalArgumentException("attempt 는 1부터: " + attempt);
    }

    /** 묶음 없는 단발 호출(분류·임베딩) — 모델은 응답의 것, 없으면 요청한 모델. */
    public static LlmCall single(Instant calledAt, LlmPurpose purpose, String requestedModel, boolean succeeded,
                                 LlmUsage usage) {
        String model = usage != null && usage.model() != null ? usage.model() : requestedModel;
        return new LlmCall(calledAt, purpose, model, null, null, 1, null, succeeded, null, usage, null);
    }
}
