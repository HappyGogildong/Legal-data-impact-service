package com.lia.core.pipeline.dispatch;

import java.util.Map;

import com.lia.core.pipeline.analyze.AnalyzeResponse;
import com.lia.core.pipeline.plan.QueryType;

/**
 * 차원별 결과 묶음(부분성공) — D46의 "주 타입 1 + 집합". 못 채운 차원은 예외가 아니라
 * {@code unmet}에 사유와 함께 담는다(fail-closed, 지어내지 않음). FE는 {@code filled}를 렌더하고
 * {@code unmet}은 안내 문구로.
 *
 * @param primaryType FE 주 뷰 차원 · @param filled 채워진 차원→응답 · @param unmet 못 채운 차원→사유
 */
public record DispatchResult(QueryType primaryType,
                             Map<QueryType, AnalyzeResponse> filled,
                             Map<QueryType, String> unmet) {

    public DispatchResult {
        filled = Map.copyOf(filled);
        unmet = Map.copyOf(unmet);
    }

    /** 요청한 모든 차원이 채워졌는가. */
    public boolean fullySatisfied() { return unmet.isEmpty(); }
}
