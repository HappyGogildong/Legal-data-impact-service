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
import com.lia.core.domain.analysis.Situation;
import com.lia.core.domain.analysis.SituationQuestion;
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

    // ── 상황 질문 세션 보관(D71) ──

    private static final LawRef SITUATION_REF = new LawRef("001809", LocalDate.of(2026, 8, 4), null);
    private static final SituationQuestion QUESTION = new SituationQuestion("q1", "계약을 이미 했나요?",
            "LAW:001809@2026-08-04:art:18", SituationQuestion.AnswerType.YES_NO, List.of());

    /** ACTION 응답이 채워진 분석 결과 — 질문이 비면 상황 없는 대응 방안. */
    private static AnalysisOutcome analyzedAction(List<SituationQuestion> questions) {
        AnalysisQuery q = new AnalysisQuery(QueryType.ACTION, Set.of(QueryType.ACTION),
                new Target.Reference(SITUATION_REF), "대응", ArticleScope.CHANGED_ONLY, false, null);
        ImpactResult action = new ImpactResult("LAW:001809@2026-08-04", "ACTION", "대응 방안", List.of(), List.of(),
                List.of(new ImpactResult.Action("신고한다", null, List.of("LAW:001809@2026-08-04:art:18"))),
                null, List.of(), "참고용", null);
        DispatchResult dr = new DispatchResult(QueryType.ACTION,
                Map.of(QueryType.ACTION, new AnalyzeResponse(action, Set.of("LAW:001809@2026-08-04:art:18"),
                        new Situation(questions, List.of()))),
                Map.of());
        return new AnalysisOutcome.Analyzed(q, dr);
    }

    @Test
    void ACTION_응답에_질문이_있으면_세션에_보관한다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);
        when(useCase.analyze(any(), any(), any(), anyBoolean())).thenReturn(analyzedAction(List.of(QUESTION)));

        controller.analyze(new AnalyzeApiRequest("주택법 대응", null, null), request);

        assertEquals(Optional.of(List.of(QUESTION)),
                new HttpSessionSituationQuestions(request.getSession(false)).find(SITUATION_REF));
    }

    @Test
    void 조문_없이_저장한_질문을_조문을_지정해_찾는다() {   // 반대 방향(조문 지정 저장 → 조문 없이 찾기)은 HttpSessionSituationQuestionsTest
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);
        when(useCase.analyze(any(), any(), any(), anyBoolean())).thenReturn(analyzedAction(List.of(QUESTION)));

        controller.analyze(new AnalyzeApiRequest("주택법 제18조 대응", null, null), request);

        assertTrue(new HttpSessionSituationQuestions(request.getSession(false))
                .find(new LawRef("001809", LocalDate.of(2026, 8, 4), "18")).isPresent());
    }

    @Test
    void 세션이_없으면_저장하지_않고_예외도_없다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(useCase.analyze(any(), any(), any(), anyBoolean())).thenReturn(analyzedAction(List.of(QUESTION)));

        assertDoesNotThrow(() -> controller.analyze(new AnalyzeApiRequest("주택법 대응", null, null), request));

        assertNull(request.getSession(false), "익명 요청에 세션을 새로 만들지 않는다");
    }

    @Test
    void 질문이_비면_저장하지_않고_기존_질문을_유지한다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        HttpSessionSituationQuestions store = new HttpSessionSituationQuestions(request.getSession(true));
        store.save(SITUATION_REF, List.of(QUESTION));
        when(useCase.analyze(any(), any(), any(), anyBoolean())).thenReturn(analyzedAction(List.of()));

        controller.analyze(new AnalyzeApiRequest("주택법 대응", null, null), request);

        assertEquals(Optional.of(List.of(QUESTION)), store.find(SITUATION_REF));
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
