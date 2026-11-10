package com.lia.core.observability.llm;

import com.lia.core.pipeline.plan.QueryType;

/** 유료 모델 호출의 목적 — {@code llm_calls.purpose}. 스펙: docs/components/observability/LlmCallRecorder.md. */
public enum LlmPurpose {
    /** 질의 분류·추출(Haiku). */
    CLASSIFY,
    /** 법령 발견 질의 임베딩(웹·평가). */
    DISCOVERY_EMBED,
    /** 시행예정 색인 임베딩(배치). */
    INDEX_EMBED,
    /** Layer A 쉬운 요약(배치 선계산). */
    SUMMARY,
    /** Layer A 변경점(배치 선계산). */
    DIFF,
    /** 첫 Layer B — 영향·대응을 한 호출로. */
    PERSONALIZED,
    /** 상황 반영 대응 방안 재생성(D71). */
    ACTION_REVISION;

    /** 분석 요청 차원 → 목적. IMPACT 는 첫 Layer B 결합 호출, ACTION 은 재생성이다(AnalyzeRequest 규약). */
    public static LlmPurpose ofAnalysis(QueryType dimension) {
        return switch (dimension) {
            case SUMMARY -> SUMMARY;
            case DIFF -> DIFF;
            case IMPACT -> PERSONALIZED;
            case ACTION -> ACTION_REVISION;
            default -> throw new IllegalArgumentException("분석 차원이 아니다: " + dimension);
        };
    }
}
