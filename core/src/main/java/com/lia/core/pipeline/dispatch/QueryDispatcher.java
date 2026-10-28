package com.lia.core.pipeline.dispatch;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.analyze.AnalyzeResponse;
import com.lia.core.pipeline.analyze.InsufficientGroundingException;
import com.lia.core.pipeline.plan.AnalysisQuery;
import com.lia.core.pipeline.plan.LawRef;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.pipeline.plan.Target;
import com.lia.core.store.LawSource;

/**
 * 검증된 {@link AnalysisQuery}를 차원별 핸들러로 라우팅·조립한다(§4#8, D47). 계획(QueryPlanner)과
 * 실행(AnalysisEngine)을 잇는 오케스트레이터. 프롬프트·LLM·인용검증은 핸들러가 위임한 AnalysisEngine 몫.
 *
 * <p>못 채우는 차원(핸들러 미구현·프로필 부족·한도·근거 부족·정본 미적재)은 예외가 아니라 {@code unmet}으로
 * 정직하게 표시한다(부분성공). {@code Target.Discovery}(코퍼스 검색)는 LookupHandler·LawDiscovery(#19)
 * 착지 전까지 전부 unmet.
 *
 * <p><b>Layer B(D69)</b> — IMPACT·ACTION 은 레지스트리가 아니라 {@link PersonalizedHandler}를 한 번 부른다.
 * 순서: 프로필 사용 불가 → 그 사유 · 개정본인데 기준선 없음 → BASELINE_NOT_LOADED · 하루 한도 초과 → DAILY_LIMIT
 * · 그 외 핸들러 1회(한도는 호출 직전 차감, 실패해도 환불 없음).
 */
@Component
public class QueryDispatcher {

    private final LawSource laws;
    private final DimensionHandlerRegistry registry;
    private final PersonalizedHandler personalized;
    private final PersonalizationQuota quota;

    public QueryDispatcher(LawSource laws, DimensionHandlerRegistry registry,
                           PersonalizedHandler personalized, PersonalizationQuota quota) {
        this.laws = laws;
        this.registry = registry;
        this.personalized = personalized;
        this.quota = quota;
    }

    /**
     * @param question        사용자 질문 원문 — Layer B 프롬프트에만 들어간다
     * @param personalization 이 요청이 Layer B 를 채울 수 있는지(application 의 PersonalizationGate 판정)
     */
    public DispatchResult dispatch(AnalysisQuery query, String question, Personalization personalization) {
        Map<QueryType, AnalyzeResponse> filled = new EnumMap<>(QueryType.class);
        Map<QueryType, Unmet> unmet = new EnumMap<>(QueryType.class);

        // Discovery(LOOKUP)는 코퍼스 검색 경로 — 현재 미구현(#19). 전 타입 unmet.
        if (query.target() instanceof Target.Discovery) {
            for (QueryType t : query.types()) {
                unmet.put(t, new Unmet(Unmet.Reason.NOT_IMPLEMENTED, "Discovery(LOOKUP) 미구현: LawDiscovery(#19) 필요"));
            }
            return new DispatchResult(query.primaryType(), filled, unmet);
        }

        // Reference — 정본·기준선을 1회 정확 조회(벡터 검색 아님).
        LawRef ref = ((Target.Reference) query.target()).lawRef();
        Optional<Law> lawOpt = laws.find(ref.lawId(), ref.effectiveDate());
        if (lawOpt.isEmpty()) {
            Unmet reason = new Unmet(Unmet.Reason.LAW_NOT_LOADED, "정본 미적재: " + ref.lawId() + "@" + ref.effectiveDate());
            for (QueryType t : query.types()) unmet.put(t, reason);
            return new DispatchResult(query.primaryType(), filled, unmet);
        }
        Law law = lawOpt.get();
        Law baseline = laws.findBaseline(ref.lawId()).orElse(null); // 제정이면 null(정상, D42)
        boolean enactment = law.amendKind() == Law.AmendKind.제정;   // 제정만 baseline이 원래 없음
        DispatchContext ctx = new DispatchContext(law, baseline, query);

        Set<QueryType> layerB = EnumSet.noneOf(QueryType.class);
        for (QueryType t : query.types()) {
            if (t.isLayerB()) {
                layerB.add(t);
                continue;
            }
            Optional<DimensionHandler> handler = registry.get(t);
            if (handler.isEmpty()) {
                unmet.put(t, new Unmet(Unmet.Reason.NOT_IMPLEMENTED, "핸들러 미구현: " + t));
                continue;
            }
            DimensionHandler h = handler.get();
            // 개정본인데 baseline이 없으면 데이터 이상 — 조용히 '제정'으로 오인하지 않는다(fail-closed).
            if (h.needsBaseline() && baseline == null && !enactment) {
                unmet.put(t, baselineMissing(law));
                continue;
            }
            try {
                filled.put(t, h.handle(ctx));
            } catch (InsufficientGroundingException e) {
                unmet.put(t, insufficientGrounding(t));   // 다른 차원은 그대로 — 부분 성공(D69)
            }
        }
        if (!layerB.isEmpty()) {
            dispatchPersonalized(ctx, layerB, enactment, question, personalization, filled, unmet);
        }
        return new DispatchResult(query.primaryType(), filled, unmet);
    }

    private void dispatchPersonalized(DispatchContext ctx, Set<QueryType> requested, boolean enactment, String question,
                                      Personalization personalization,
                                      Map<QueryType, AnalyzeResponse> filled, Map<QueryType, Unmet> unmet) {
        if (personalization instanceof Personalization.Blocked blocked) {
            Unmet reason = new Unmet(blocked.reason(), "개인화 불가 (Layer B): " + blocked.reason());
            for (QueryType t : requested) unmet.put(t, reason);
            return;
        }
        Personalization.Usable usable = (Personalization.Usable) personalization;
        if (ctx.baseline() == null && !enactment) {
            Unmet reason = baselineMissing(ctx.law());
            for (QueryType t : requested) unmet.put(t, reason);
            return;
        }
        if (!quota.tryConsume(usable.userId())) {
            Unmet reason = new Unmet(Unmet.Reason.DAILY_LIMIT, "오늘 개인화 분석 한도 초과");
            for (QueryType t : requested) unmet.put(t, reason);
            return;
        }
        try {
            filled.putAll(personalized.handle(ctx, usable.profile(), question, requested));
        } catch (InsufficientGroundingException e) {
            for (QueryType t : requested) unmet.put(t, insufficientGrounding(t));
        }
    }

    private static Unmet baselineMissing(Law law) {
        return new Unmet(Unmet.Reason.BASELINE_NOT_LOADED,
                "기준선 미적재 — 개정본(" + law.amendKind() + ")인데 시행중본 없음");
    }

    private static Unmet insufficientGrounding(QueryType type) {
        return new Unmet(Unmet.Reason.INSUFFICIENT_GROUNDING, "근거 부족 — 재생성 후에도 인용 검증 실패: " + type);
    }
}
