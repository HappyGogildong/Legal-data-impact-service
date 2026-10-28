package com.lia.core.pipeline.dispatch;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.analyze.AnalysisContext;
import com.lia.core.pipeline.analyze.AnalysisEngine;
import com.lia.core.pipeline.analyze.AnalyzeResponse;
import com.lia.core.pipeline.analyze.ContextBuilder;
import com.lia.core.pipeline.analyze.Reasoner;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.profile.UserProfile;
import com.lia.core.testsupport.TestLaws;

/** PersonalizedHandler — 두 차원을 요청해도 엔진(추론)은 1회, 결과를 IMPACT·ACTION 으로 나눈다(D69). */
class PersonalizedHandlerTest {

    private static final Law LAW = TestLaws.law("A", "M1", Law.Status.시행예정, LocalDate.of(2027, 1, 1));
    private static final UserProfile PROFILE = new UserProfile(null, 34, null, null, null, null, null);

    private final List<AnalysisContext> calls = new ArrayList<>();

    /** 받은 컨텍스트를 기록하고, 주입된 첫 source_id 를 인용한 Layer B 결과를 돌려준다. */
    private final Reasoner reasoner = ctx -> {
        calls.add(ctx);
        String cite = ctx.injectedSourceIds().iterator().next();
        return new ImpactResult(ctx.lawRef(), "IMPACT", "요약",
                List.of(new ImpactResult.Claim("주장", List.of(cite), 0.9)),
                List.of(new ImpactResult.Impact("주거", "영향 있음", "상세", List.of(cite))),
                List.of(new ImpactResult.Action("확인", "2027-01-01", List.of(cite))),
                new ImpactResult.EffectiveInfo("시행예정", LocalDate.of(2027, 1, 1), "즉시"),
                List.of("불확실"), "참고용", null);
    };

    private PersonalizedHandler handler() {
        return new PersonalizedHandler(new AnalysisEngine(new ContextBuilder(), reasoner, 3));
    }

    private static DispatchContext ctx() {
        return new DispatchContext(LAW, null, null);
    }

    @Test
    void 두_차원을_요청해도_추론은_한_번이고_결과를_나눈다() {
        Map<QueryType, AnalyzeResponse> out = handler().handle(ctx(), PROFILE, "내 영향",
                EnumSet.of(QueryType.IMPACT, QueryType.ACTION));

        assertEquals(1, calls.size(), "Opus 1회");
        assertSame(PROFILE, calls.get(0).profile());
        assertEquals("내 영향", calls.get(0).question());

        ImpactResult impact = out.get(QueryType.IMPACT).result();
        assertEquals("IMPACT", impact.command());
        assertEquals(1, impact.impacts().size());
        assertTrue(impact.actions().isEmpty(), "IMPACT 에는 대응 없음");
        assertEquals("요약", impact.summary());

        ImpactResult action = out.get(QueryType.ACTION).result();
        assertEquals("ACTION", action.command());
        assertEquals(1, action.actions().size());
        assertTrue(action.impacts().isEmpty(), "ACTION 에는 영향 없음");
        assertNotNull(action.effectiveInfo());
        assertEquals(List.of("불확실"), action.uncertainties());
    }

    @Test
    void 요청한_차원만_채운다() {
        Map<QueryType, AnalyzeResponse> out = handler().handle(ctx(), PROFILE, "할 일", EnumSet.of(QueryType.ACTION));

        assertEquals(EnumSet.of(QueryType.ACTION), EnumSet.copyOf(out.keySet()));
        assertEquals(1, calls.size());
    }
}
