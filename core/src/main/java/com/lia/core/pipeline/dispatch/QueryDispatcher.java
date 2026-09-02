package com.lia.core.pipeline.dispatch;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.analyze.AnalyzeResponse;
import com.lia.core.pipeline.plan.AnalysisQuery;
import com.lia.core.pipeline.plan.LawRef;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.pipeline.plan.Target;
import com.lia.core.store.LawSource;

/**
 * 검증된 {@link AnalysisQuery}를 차원별 핸들러로 라우팅·조립한다(§4#8, D47). 계획(QueryPlanner)과
 * 실행(AnalysisEngine)을 잇는 오케스트레이터. 프롬프트·LLM·인용검증은 핸들러가 위임한 AnalysisEngine 몫.
 *
 * <p>못 채우는 차원(핸들러 미구현·프로필 부족·정본 미적재)은 예외가 아니라 {@code unmet}으로
 * 정직하게 표시한다(부분성공). {@code Target.Discovery}(코퍼스 검색)는 LookupHandler·LawDiscovery(#19)
 * 착지 전까지 전부 unmet.
 */
@Component
public class QueryDispatcher {

    private final LawSource laws;
    private final DimensionHandlerRegistry registry;

    public QueryDispatcher(LawSource laws, DimensionHandlerRegistry registry) {
        this.laws = laws;
        this.registry = registry;
    }

    public DispatchResult dispatch(AnalysisQuery query) {
        Map<QueryType, AnalyzeResponse> filled = new EnumMap<>(QueryType.class);
        Map<QueryType, String> unmet = new EnumMap<>(QueryType.class);

        // Discovery(LOOKUP)는 코퍼스 검색 경로 — 현재 미구현(#19). 전 타입 unmet.
        if (query.target() instanceof Target.Discovery) {
            for (QueryType t : query.types()) {
                unmet.put(t, "Discovery(LOOKUP) 미구현: LawDiscovery(#19) 필요");
            }
            return new DispatchResult(query.primaryType(), filled, unmet);
        }

        // Reference — 정본·기준선을 1회 정확 조회(벡터 검색 아님).
        LawRef ref = ((Target.Reference) query.target()).lawRef();
        Optional<Law> lawOpt = laws.find(ref.lawId(), ref.effectiveDate());
        if (lawOpt.isEmpty()) {
            String reason = "정본 미적재: " + ref.lawId() + "@" + ref.effectiveDate();
            for (QueryType t : query.types()) unmet.put(t, reason);
            return new DispatchResult(query.primaryType(), filled, unmet);
        }
        Law baseline = laws.findBaseline(ref.lawId()).orElse(null); // 제정이면 null(정상, D42)
        DispatchContext ctx = new DispatchContext(lawOpt.get(), baseline, query);

        for (QueryType t : query.types()) {
            Optional<DimensionHandler> handler = registry.get(t);
            if (handler.isEmpty()) {
                unmet.put(t, "핸들러 미구현: " + t);
                continue;
            }
            DimensionHandler h = handler.get();
            if (h.needsProfile() && !query.profileBound()) {
                unmet.put(t, "프로필 필요 (Layer B)");
                continue;
            }
            filled.put(t, h.handle(ctx));
        }
        return new DispatchResult(query.primaryType(), filled, unmet);
    }
}
