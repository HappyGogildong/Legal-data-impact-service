package com.lia.core.observability.llm;

/**
 * 유료 모델 호출 기록 포트(D83). 구현은 본 처리를 막거나 실패시키지 않는다 — 스펙:
 * docs/components/observability/LlmCallRecorder.md.
 */
@FunctionalInterface
public interface LlmCallRecorder {

    /** 아무것도 남기지 않는다(기록을 다루지 않는 단위 테스트·도구용). */
    LlmCallRecorder NONE = call -> {};

    void record(LlmCall call);
}
