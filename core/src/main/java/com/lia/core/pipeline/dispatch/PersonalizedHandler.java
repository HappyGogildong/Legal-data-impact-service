package com.lia.core.pipeline.dispatch;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.pipeline.analyze.AnalysisEngine;
import com.lia.core.pipeline.analyze.AnalyzeRequest;
import com.lia.core.pipeline.analyze.AnalyzeResponse;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.profile.UserProfile;

/**
 * Layer B(내 영향·대응 방안, D69) — IMPACT·ACTION 을 <b>엔진 1회</b>로 만들고 결과를 차원별로 나눈다.
 * 레지스트리 핸들러(차원당 1회)와 달리 디스패처가 직접 한 번 부른다. 프로필·질문은 이 호출 인자로만 받는다
 * — Layer A 핸들러에는 구조적으로 닿지 않는다. 개인화 답은 저장하지 않는다(캐시 없음).
 *
 * <p>상황 질문·단계 설명은 대응 방안(ACTION) 응답에 싣는다(D71) — 내 영향(IMPACT)은 상황 없음.
 */
@Component
public class PersonalizedHandler {

    private final AnalysisEngine engine;

    public PersonalizedHandler(AnalysisEngine engine) {
        this.engine = engine;
    }

    /** 요청된 Layer B 차원만 채운다. 근거 부족이면 {@code InsufficientGroundingException} 이 그대로 올라간다(디스패처가 unmet 으로). */
    public Map<QueryType, AnalyzeResponse> handle(DispatchContext ctx, UserProfile profile, String question,
                                                  Set<QueryType> requested) {
        AnalyzeResponse response = engine.analyze(
                new AnalyzeRequest(QueryType.IMPACT, ctx.law(), ctx.baseline(), profile, question));
        ImpactResult r = response.result();
        Map<QueryType, AnalyzeResponse> out = new EnumMap<>(QueryType.class);
        if (requested.contains(QueryType.IMPACT)) {
            out.put(QueryType.IMPACT, new AnalyzeResponse(impactPart(r), response.injectedSourceIds()));
        }
        if (requested.contains(QueryType.ACTION)) {
            out.put(QueryType.ACTION, new AnalyzeResponse(actionPart(r), response.injectedSourceIds(), response.situation()));
        }
        return out;
    }

    /** 내 영향 — 요약·주장·영향·불확실성(대응·시행 정보는 ACTION 몫). */
    private static ImpactResult impactPart(ImpactResult r) {
        return new ImpactResult(r.lawRef(), QueryType.IMPACT.name(), r.summary(), r.claims(), r.impacts(),
                List.of(), null, r.uncertainties(), r.disclaimer(), r.meta());
    }

    /** 대응 방안 — 할 일·시행 정보·불확실성. 대응 방안 재생성(D71)도 같은 모양을 쓴다. */
    public static ImpactResult actionPart(ImpactResult r) {
        return new ImpactResult(r.lawRef(), QueryType.ACTION.name(), null, List.of(), List.of(),
                r.actions(), r.effectiveInfo(), r.uncertainties(), r.disclaimer(), r.meta());
    }
}
