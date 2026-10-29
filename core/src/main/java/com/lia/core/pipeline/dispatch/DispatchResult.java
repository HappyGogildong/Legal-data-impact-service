package com.lia.core.pipeline.dispatch;

import java.util.Map;

import com.lia.core.pipeline.analyze.AnalyzeResponse;
import com.lia.core.pipeline.discover.DiscoveryResult;
import com.lia.core.pipeline.plan.QueryType;

/**
 * 차원별 결과 묶음(부분성공) — D46의 "주 타입 1 + 집합". 못 채운 분석 차원은 예외가 아니라
 * {@code unmet}에 사유와 함께 담는다(fail-closed, 지어내지 않음). 법령 발견(LOOKUP)의 결과는 차원이 아니라
 * {@code discovery} 에 담는다(D70 — Reference 경로에선 null).
 *
 * @param primaryType FE 주 뷰 차원 · @param filled 채워진 차원→응답 · @param unmet 못 채운 차원→사유(코드·설명)
 * @param discovery   발견 결과(Discovery 질의만)
 */
public record DispatchResult(QueryType primaryType,
                             Map<QueryType, AnalyzeResponse> filled,
                             Map<QueryType, Unmet> unmet,
                             Discovery discovery) {

    public DispatchResult {
        filled = Map.copyOf(filled);
        unmet = Map.copyOf(unmet);
    }

    public DispatchResult(QueryType primaryType, Map<QueryType, AnalyzeResponse> filled, Map<QueryType, Unmet> unmet) {
        this(primaryType, filled, unmet, null);
    }

    /** 요청한 모든 차원이 채워졌는가. */
    public boolean fullySatisfied() { return unmet.isEmpty(); }

    /**
     * @param found          후보 목록
     * @param profileBlocked "내 프로필 기준으로 찾기"를 켰지만 프로필을 못 쓴 사유(그땐 질문만으로 찾았다), 아니면 null
     */
    public record Discovery(DiscoveryResult found, Unmet profileBlocked) {}
}
