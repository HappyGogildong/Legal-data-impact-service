package com.lia.core.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.lia.core.application.analysis.AnalysisOutcome;
import com.lia.core.application.analysis.AnalyzeUseCase;
import com.lia.core.auth.CurrentUser;
import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.analyze.AnalyzeResponse;
import com.lia.core.pipeline.discover.DiscoveredLaw;
import com.lia.core.pipeline.discover.DiscoveryResult;
import com.lia.core.pipeline.dispatch.DispatchResult;
import com.lia.core.pipeline.dispatch.Unmet;
import com.lia.core.pipeline.plan.AnalysisQuery;
import com.lia.core.pipeline.plan.ArticleScope;
import com.lia.core.pipeline.plan.DiscoveryCriteria;
import com.lia.core.pipeline.plan.LawRef;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.pipeline.plan.Target;
import com.lia.core.pipeline.resolve.ResolutionResult;
import com.lia.core.store.LawSource;

/**
 * AnalysisController 단위 — 위임·상태·검증(빈 query)만 본다. AnalyzeUseCase는 목, 매핑은 실 매퍼(빈 정본).
 * HTTP 라우팅은 수동 curl, 응답 스키마는 OpenApiSnapshotTest, JSON 모양은 AnalysisResponseMapperTest.직렬화.
 */
class AnalysisControllerTest {

    static final LawSource EMPTY = new LawSource() {
        public Optional<Law> find(String lawId, LocalDate efYd) { return Optional.empty(); }
        public Optional<Law> findBaseline(String lawId) { return Optional.empty(); }
    };

    private final AnalyzeUseCase useCase = mock(AnalyzeUseCase.class);
    private final AnalysisController controller =
            new AnalysisController(useCase, new AnalysisResponseMapper(EMPTY, new CitedSourceResolver(EMPTY)));

    private static AnalysisOutcome analyzed() {
        LawRef ref = new LawRef("001809", LocalDate.of(2026, 8, 4), null);
        AnalysisQuery q = new AnalysisQuery(QueryType.SUMMARY, Set.of(QueryType.SUMMARY),
                new Target.Reference(ref), "요약", ArticleScope.CHANGED_ONLY, false, null);
        ImpactResult ir = new ImpactResult("LAW:001809@2026-08-04", "SUMMARY", "주택법 개정 요약",
                List.of(new ImpactResult.Claim("개정된다", List.of("LAW:001809@2026-08-04:art:18"), 0.9)),
                List.of(), List.of(), null, List.of("일부 대통령령 위임"), "참고용", null);
        DispatchResult dr = new DispatchResult(QueryType.SUMMARY,
                Map.of(QueryType.SUMMARY, new AnalyzeResponse(ir, Set.of("LAW:001809@2026-08-04:art:18"))),
                Map.of(QueryType.IMPACT, new Unmet(Unmet.Reason.PROFILE_REQUIRED, "프로필 필요 (Layer B)")));
        return new AnalysisOutcome.Analyzed(q, dr);
    }

    @Test
    void 분석되면_RESOLVED_law_answer_unmet을_매핑한다() {
        when(useCase.analyze(any(), any(), any(), anyBoolean())).thenReturn(analyzed());

        ResponseEntity<AnalysisApiResponse> resp =
                controller.analyze(new AnalyzeApiRequest("주택법 뭐가 바뀌어?", null, null), new MockHttpServletRequest());

        assertEquals(200, resp.getStatusCode().value());
        AnalysisApiResponse body = resp.getBody();
        assertEquals("RESOLVED", body.resolution());
        assertEquals("LAW:001809@2026-08-04", body.law().ref());
        assertTrue(body.answer().containsKey("summary"), "채워진 차원 소문자 키");
        assertTrue(body.unmet().containsKey("impact"), "못 채운 차원+사유");
        assertEquals("참고용", body.disclaimer());
    }

    @Test
    void 빈_query는_IllegalArgumentException_400매핑() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.analyze(new AnalyzeApiRequest("   ", null, null), new MockHttpServletRequest()));
    }

    @Test
    void 미해소면_resolution과_message를_200으로_전달한다() {
        when(useCase.analyze(any(), any(), any(), anyBoolean()))
                .thenReturn(new AnalysisOutcome.Unresolved(ResolutionResult.notFoundYet("그 법을 아직 못 찾았어요.")));

        ResponseEntity<AnalysisApiResponse> resp =
                controller.analyze(new AnalyzeApiRequest("어떤 이상한 법", null, null), new MockHttpServletRequest());

        assertEquals(200, resp.getStatusCode().value());
        assertEquals("NOT_FOUND_YET", resp.getBody().resolution());
        assertEquals("그 법을 아직 못 찾았어요.", resp.getBody().message());
    }

    @Test
    void 세션이_있으면_userId_를_유스케이스에_넘긴다() {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute(CurrentUser.SESSION_KEY, userId);
        when(useCase.analyze(eq("주택법 내 영향"), isNull(), eq(Optional.of(userId)), eq(false)))
                .thenReturn(new AnalysisOutcome.Unresolved(ResolutionResult.notFoundYet("그 법을 아직 못 찾았어요.")));

        controller.analyze(new AnalyzeApiRequest("주택법 내 영향", null, null), request);

        verify(useCase).analyze("주택법 내 영향", null, Optional.of(userId), false);
    }

    @Test
    void 세션이_없으면_익명으로_넘긴다() {
        when(useCase.analyze(any(), any(), any(), anyBoolean()))
                .thenReturn(new AnalysisOutcome.Unresolved(ResolutionResult.notFoundYet("x")));

        controller.analyze(new AnalyzeApiRequest("주택법 내 영향", null, null), new MockHttpServletRequest());

        verify(useCase).analyze("주택법 내 영향", null, Optional.empty(), false);
    }

    @Test
    void profileSearch_를_유스케이스에_넘긴다() {
        when(useCase.analyze(any(), any(), any(), anyBoolean()))
                .thenReturn(new AnalysisOutcome.Unresolved(ResolutionResult.notFoundYet("x")));

        controller.analyze(new AnalyzeApiRequest("전세 관련 법", null, null, true), new MockHttpServletRequest());

        verify(useCase).analyze("전세 관련 법", null, Optional.empty(), true);
    }

    @Test
    void 발견이면_RESOLVED_와_discovery_만() {
        AnalysisQuery q = new AnalysisQuery(QueryType.LOOKUP, Set.of(QueryType.LOOKUP),
                new Target.Discovery(new DiscoveryCriteria(List.of(), List.of(), List.of(), false)),
                "찾기", ArticleScope.CHANGED_ONLY, false, null);
        DiscoveredLaw card = new DiscoveredLaw("001809", "주택법", LocalDate.of(2026, 8, 4), "국토교통부",
                "거주 의무가 바뀌어요.", true, new DiscoveredLaw.MatchedSource("LAW:001809@2026-08-04:art:46", "제46조"));
        DispatchResult dr = new DispatchResult(QueryType.LOOKUP, Map.of(), Map.of(),
                new DispatchResult.Discovery(new DiscoveryResult(List.of(card), false),
                        new Unmet(Unmet.Reason.PROFILE_REQUIRED, "프로필 기준으로 찾을 수 없음")));
        when(useCase.analyze(any(), any(), any(), anyBoolean())).thenReturn(new AnalysisOutcome.Analyzed(q, dr));

        AnalysisApiResponse body = controller.analyze(
                new AnalyzeApiRequest("전세 관련 법", null, null, true), new MockHttpServletRequest()).getBody();

        assertEquals("RESOLVED", body.resolution());
        assertNull(body.law());
        assertNull(body.answer());
        assertEquals(List.of(card), body.discovery().candidates());
        assertFalse(body.discovery().profileApplied());
        assertEquals(Unmet.Reason.PROFILE_REQUIRED, body.discovery().profileBlocked().reason());
    }
}
