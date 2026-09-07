package com.lia.core.web;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.lia.core.application.analysis.AnalysisOutcome;
import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.pipeline.analyze.AnalyzeResponse;
import com.lia.core.pipeline.connector.RawLaw;
import com.lia.core.pipeline.dispatch.DispatchResult;
import com.lia.core.pipeline.plan.AnalysisQuery;
import com.lia.core.pipeline.plan.ArticleScope;
import com.lia.core.pipeline.plan.LawRef;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.pipeline.plan.Target;
import com.lia.core.pipeline.resolve.ResolutionResult;

/** AnalysisResponseMapper 단위 — 도메인 결과 → 스펙 응답 Map 매핑(§3.0)을 컨트롤러 없이 직접 검증. */
class AnalysisResponseMapperTest {

    private final AnalysisResponseMapper mapper = new AnalysisResponseMapper();

    private static AnalysisOutcome.Analyzed analyzed() {
        LawRef ref = new LawRef("001809", LocalDate.of(2026, 8, 4), null);
        AnalysisQuery q = new AnalysisQuery(QueryType.SUMMARY, Set.of(QueryType.SUMMARY),
                new Target.Reference(ref), "요약", ArticleScope.CHANGED_ONLY, false, null);
        ImpactResult ir = new ImpactResult("LAW:001809@2026-08-04", "SUMMARY", "주택법 개정 요약",
                List.of(new ImpactResult.Claim("개정된다", List.of("LAW:001809@2026-08-04:art:18"), 0.9)),
                List.of(), List.of(), null, List.of("일부 위임"), "참고용", null);
        DispatchResult dr = new DispatchResult(QueryType.SUMMARY,
                Map.of(QueryType.SUMMARY, new AnalyzeResponse(ir, Set.of("LAW:001809@2026-08-04:art:18"))),
                Map.of(QueryType.IMPACT, "프로필 필요 (Layer B)"));
        return new AnalysisOutcome.Analyzed(q, dr);
    }

    @Test
    @SuppressWarnings("unchecked")
    void analyzed는_resolution_lawref_answer_unmet_disclaimer를_담는다() {
        Map<String, Object> body = mapper.toBody(analyzed());

        assertEquals("RESOLVED", body.get("resolution"));
        assertEquals("LAW:001809@2026-08-04", body.get("law_ref"));
        assertTrue(((Map<String, Object>) body.get("answer")).containsKey("summary"), "차원 소문자 키");
        assertTrue(((List<String>) body.get("unmet")).contains("impact"));
        assertEquals("참고용", body.get("disclaimer"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void unresolved는_resolution_message_candidates를_담는다() {
        RawLaw c = new RawLaw("001809", "283191", "주택법", "시행예정",
                LocalDate.of(2026, 8, 4), LocalDate.of(2026, 2, 3), "21323", null);
        AnalysisOutcome out = new AnalysisOutcome.Unresolved(
                ResolutionResult.ambiguous(List.of(c), "여러 후보가 있어요."));

        Map<String, Object> body = mapper.toBody(out);

        assertEquals("AMBIGUOUS", body.get("resolution"));
        assertEquals("여러 후보가 있어요.", body.get("message"));
        List<Map<String, Object>> cands = (List<Map<String, Object>>) body.get("candidates");
        assertEquals("001809", cands.get(0).get("lawId"));
        assertEquals("주택법", cands.get(0).get("title"));
    }
}
