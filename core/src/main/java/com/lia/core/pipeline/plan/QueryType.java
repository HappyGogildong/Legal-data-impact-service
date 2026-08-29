package com.lia.core.pipeline.plan;

/**
 * 질의가 요구하는 분석 차원(D46). {@code LOOKUP}=발견, 나머지는 분석 차원.
 * Layer A(선계산·no-LLM): {@code SUMMARY}·{@code DIFF} / Layer B(프로필별·온라인 LLM): {@code IMPACT}·{@code ACTION}.
 */
public enum QueryType {
    LOOKUP, SUMMARY, DIFF, IMPACT, ACTION;

    /** 프로필이 있어야 채울 수 있는 차원(온라인 LLM). */
    public boolean isLayerB() {
        return this == IMPACT || this == ACTION;
    }
}
