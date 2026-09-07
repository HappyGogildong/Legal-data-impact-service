package com.lia.core.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.pipeline.analyze.AnalyzeResponse;
import com.lia.core.pipeline.dispatch.DispatchResult;
import com.lia.core.pipeline.plan.AnalysisQuery;
import com.lia.core.pipeline.plan.ArticleScope;
import com.lia.core.pipeline.plan.LawRef;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.pipeline.plan.Target;
import com.lia.core.pipeline.resolve.ResolutionResult;

/**
 * AnalysisController 단위 — 응답 매핑(스펙 키)·검증(빈 query)만 본다. AnalysisService는 목.
 * HTTP 라우팅/상태코드/직렬화는 수동 curl(검증 2단계)로 확인(Boot 4.0 test-slice 미사용).
 */
class AnalysisControllerTest {

    private final AnalysisService service = mock(AnalysisService.class);
    private final AnalysisController controller = new AnalysisController(service);

    private static AnalysisOutcome analyzed() {
        LawRef ref = new LawRef("001809", LocalDate.of(2026, 8, 4), null);
        AnalysisQuery q = new AnalysisQuery(QueryType.SUMMARY, Set.of(QueryType.SUMMARY),
                new Target.Reference(ref), "요약", ArticleScope.CHANGED_ONLY, false, null);
        ImpactResult ir = new ImpactResult("LAW:001809@2026-08-04", "SUMMARY", "주택법 개정 요약",
                List.of(new ImpactResult.Claim("개정된다", List.of("LAW:001809@2026-08-04:art:18"), 0.9)),
                List.of(), List.of(), null, List.of("일부 대통령령 위임"), "참고용", null);
        DispatchResult dr = new DispatchResult(QueryType.SUMMARY,
                Map.of(QueryType.SUMMARY, new AnalyzeResponse(ir, Set.of("LAW:001809@2026-08-04:art:18"))),
                Map.of(QueryType.IMPACT, "프로필 필요 (Layer B)"));
        return new AnalysisOutcome.Analyzed(q, dr);
    }

    @SuppressWarnings("unchecked")
    @Test
    void 분석되면_RESOLVED_law_ref_answer_unmet을_매핑한다() {
        when(service.analyze(any(), any())).thenReturn(analyzed());

        ResponseEntity<Map<String, Object>> resp =
                controller.analyze(new AnalyzeApiRequest("주택법 뭐가 바뀌어?", null, null));

        assertEquals(200, resp.getStatusCode().value());
        Map<String, Object> body = resp.getBody();
        assertEquals("RESOLVED", body.get("resolution"));
        assertEquals("LAW:001809@2026-08-04", body.get("law_ref"));
        assertTrue(((Map<String, Object>) body.get("answer")).containsKey("summary"), "채워진 차원 소문자 키");
        assertTrue(((List<String>) body.get("unmet")).contains("impact"), "못 채운 차원 표기");
        assertEquals("참고용", body.get("disclaimer"));
    }

    @Test
    void 빈_query는_IllegalArgumentException_400매핑() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.analyze(new AnalyzeApiRequest("   ", null, null)));
        // ApiExceptionHandler가 IllegalArgumentException → 400으로 매핑(§4.1).
    }

    @Test
    void 미해소면_resolution과_message를_200으로_전달한다() {
        when(service.analyze(any(), any()))
                .thenReturn(new AnalysisOutcome.Unresolved(ResolutionResult.notFoundYet("그 법을 아직 못 찾았어요.")));

        ResponseEntity<Map<String, Object>> resp =
                controller.analyze(new AnalyzeApiRequest("어떤 이상한 법", null, null));

        assertEquals(200, resp.getStatusCode().value());
        assertEquals("NOT_FOUND_YET", resp.getBody().get("resolution"));
        assertEquals("그 법을 아직 못 찾았어요.", resp.getBody().get("message"));
    }
}
