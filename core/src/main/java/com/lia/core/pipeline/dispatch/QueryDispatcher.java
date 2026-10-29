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
import com.lia.core.pipeline.discover.LawDiscovery;
import com.lia.core.pipeline.plan.AnalysisQuery;
import com.lia.core.pipeline.plan.LawRef;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.pipeline.plan.Target;
import com.lia.core.profile.UserProfile;
import com.lia.core.store.LawSource;

/**
 * 검증된 {@link AnalysisQuery}를 차원별 핸들러로 라우팅·조립한다(§4#8, D47). 계획(QueryPlanner)과
 * 실행(AnalysisEngine)을 잇는 오케스트레이터. 프롬프트·LLM·인용검증은 핸들러가 위임한 AnalysisEngine 몫.
 *
 * <p>못 채우는 차원(핸들러 미구현·프로필 부족·한도·근거 부족·정본 미적재)은 예외가 아니라 {@code unmet}으로
 * 정직하게 표시한다(부분성공). {@code Target.Discovery} 는 {@link LawDiscovery} 를 한 번 불러
 * {@code discovery} 에 담는다(D70 — 한도·LLM 없음).
 *
 * <p><b>Layer B(D69)</b> — IMPACT·ACTION 은 레지스트리가 아니라 {@link PersonalizedHandler}를 한 번 부른다.
 * 순서: 프로필 사용 불가 → 그 사유 · {@link PersonalAdmission}(개정본인데 기준선 없음 → BASELINE_NOT_LOADED ·
 * 하루 한도 초과 → DAILY_LIMIT) · 그 외 핸들러 1회(한도는 호출 직전 차감, 실패해도 환불 없음).
 */
@Component
public class QueryDispatcher {

    private final LawSource laws;
    private final DimensionHandlerRegistry registry;
    private final PersonalizedHandler personalized;
    private final PersonalizationQuota quota;
    private final LawDiscovery discovery;

    public QueryDispatcher(LawSource laws, DimensionHandlerRegistry registry,
                           PersonalizedHandler personalized, PersonalizationQuota quota, LawDiscovery discovery) {
        this.laws = laws;
        this.registry = registry;
        this.personalized = personalized;
        this.quota = quota;
        this.discovery = discovery;
    }

    /** {@code profileSearch=false} — 프로필 기준 찾기를 쓰지 않는 기본 경로. */
    public DispatchResult dispatch(AnalysisQuery query, String question, Personalization personalization) {
        return dispatch(query, question, personalization, false);
    }

    /**
     * @param question        사용자 질문 원문 — Layer B 프롬프트·발견 검색어에만 쓰인다
     * @param personalization 이 요청이 프로필을 쓸 수 있는지(application 의 PersonalizationGate 판정)
     * @param profileSearch   법령 발견에서 "내 프로필 기준으로 찾기"를 켰는가(D70)
     */
    public DispatchResult dispatch(AnalysisQuery query, String question, Personalization personalization,
                                   boolean profileSearch) {
        Map<QueryType, AnalyzeResponse> filled = new EnumMap<>(QueryType.class);
        Map<QueryType, Unmet> unmet = new EnumMap<>(QueryType.class);

        // Discovery(LOOKUP) — 후보만(D70). 차원 결과가 아니라 discovery 에 담는다.
        if (query.target() instanceof Target.Discovery) {
            return new DispatchResult(query.primaryType(), filled, unmet,
                    discover(question, personalization, profileSearch));
        }

        // Reference — 정본·기준선을 1회 정확 조회(벡터 검색 아님).
        LawRef ref = ((Target.Reference) query.target()).lawRef();
        Optional<Law> lawOpt = laws.find(ref.lawId(), ref.effectiveDate());
        if (lawOpt.isEmpty()) {
            Unmet reason = PersonalAdmission.lawNotLoaded(ref.lawId(), ref.effectiveDate());
            for (QueryType t : query.types()) unmet.put(t, reason);
            return new DispatchResult(query.primaryType(), filled, unmet);
        }
        Law law = lawOpt.get();
        Law baseline = laws.findBaseline(ref.lawId()).orElse(null); // 제정이면 null(정상, D42)
        boolean enactment = law.amendKind() == Law.AmendKind.제정;   // 제정만 baseline이 원래 없음
        DispatchContext ctx = new DispatchContext(law, baseline, query);

        Set<QueryType> personalizedTypes = EnumSet.noneOf(QueryType.class);
        for (QueryType t : query.types()) {
            if (t.isPersonalized()) {
                personalizedTypes.add(t);
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
                unmet.put(t, PersonalAdmission.baselineMissing(law));
                continue;
            }
            try {
                filled.put(t, h.handle(ctx));
            } catch (InsufficientGroundingException e) {
                unmet.put(t, PersonalAdmission.insufficientGrounding(t));   // 다른 차원은 그대로 — 부분 성공(D69)
            }
        }
        if (!personalizedTypes.isEmpty()) {
            dispatchPersonalized(ctx, personalizedTypes, question, personalization, filled, unmet);
        }
        return new DispatchResult(query.primaryType(), filled, unmet);
    }

    /** 토글을 켰고 프로필을 쓸 수 있으면 그 프로필로, 못 쓰면 질문만으로 찾고 사유를 싣는다. */
    private DispatchResult.Discovery discover(String question, Personalization personalization, boolean profileSearch) {
        Optional<UserProfile> profile = Optional.empty();
        Unmet blocked = null;
        if (profileSearch) {
            switch (personalization) {
                case Personalization.Usable usable -> profile = Optional.of(usable.profile());
                case Personalization.Blocked b ->
                        blocked = new Unmet(b.reason(), "프로필 기준으로 찾을 수 없음: " + b.reason());
            }
        }
        return new DispatchResult.Discovery(discovery.discover(question, profile), blocked);
    }

    private void dispatchPersonalized(DispatchContext ctx, Set<QueryType> requested, String question,
                                      Personalization personalization,
                                      Map<QueryType, AnalyzeResponse> filled, Map<QueryType, Unmet> unmet) {
        if (personalization instanceof Personalization.Blocked blocked) {
            Unmet reason = new Unmet(blocked.reason(), "개인화 불가 (Layer B): " + blocked.reason());
            for (QueryType t : requested) unmet.put(t, reason);
            return;
        }
        Personalization.Usable usable = (Personalization.Usable) personalization;
        Optional<Unmet> refused = PersonalAdmission.admit(usable.userId(), ctx.law(), ctx.baseline(), quota);
        if (refused.isPresent()) {
            for (QueryType t : requested) unmet.put(t, refused.get());
            return;
        }
        try {
            filled.putAll(personalized.handle(ctx, usable.profile(), question, requested));
        } catch (InsufficientGroundingException e) {
            for (QueryType t : requested) unmet.put(t, PersonalAdmission.insufficientGrounding(t));
        }
    }
}
