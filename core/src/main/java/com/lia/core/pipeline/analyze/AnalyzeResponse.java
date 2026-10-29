package com.lia.core.pipeline.analyze;

import java.util.Set;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.analysis.Situation;

/**
 * 분석 응답 — 검증 통과한 {@link ImpactResult} + <b>주입 source_id 집합</b>(2차 검증 게이트 #12 입력)
 * + (개인화) 상황 정보(D71).
 */
public record AnalyzeResponse(ImpactResult result, Set<String> injectedSourceIds, Situation situation) {
    public AnalyzeResponse {
        situation = situation == null ? Situation.none() : situation;
    }

    public AnalyzeResponse(ImpactResult result, Set<String> injectedSourceIds) {
        this(result, injectedSourceIds, Situation.none());
    }
}
