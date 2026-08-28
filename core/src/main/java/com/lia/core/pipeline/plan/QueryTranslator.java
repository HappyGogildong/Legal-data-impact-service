package com.lia.core.pipeline.plan;

/**
 * 자연어 → {@link AnalysisQueryDraft} 번역 <b>포트</b> — 파이프라인의 <b>유일한 LLM 자유도</b>(D46).
 *
 * <p>포트로 두는 이유는 "교체 예정"이 아니라 <b>테스트 심</b>이다: 플래너의 결정론 로직을 실 LLM 없이
 * (비용 0·결정론) 검증하려면 번역 결과를 가짜로 주입해야 한다. 구현 {@code SpringAiQueryTranslator}
 * (Haiku 4.5). 스펙: docs/components/QueryPlanner.md §3.
 */
public interface QueryTranslator {

    AnalysisQueryDraft translate(String query);
}
