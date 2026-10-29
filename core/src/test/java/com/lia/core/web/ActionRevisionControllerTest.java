package com.lia.core.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.lia.core.application.analysis.ActionRevisionUseCase;
import com.lia.core.application.analysis.RevisionOutcome;
import com.lia.core.auth.CurrentUser;
import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.analysis.Situation;
import com.lia.core.domain.analysis.SituationAnswer;
import com.lia.core.domain.analysis.StepNote;
import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.analyze.AnalyzeResponse;
import com.lia.core.pipeline.dispatch.Unmet;
import com.lia.core.pipeline.plan.LawRef;
import com.lia.core.store.LawSource;

/** ActionRevisionController 단위 — 위임(userId·도메인 lawRef·답·세션 어댑터)·응답 모양·검증. 유스케이스는 목. */
class ActionRevisionControllerTest {

    static final LocalDate EF = LocalDate.of(2026, 8, 4);
    static final String CITE = "LAW:001809@2026-08-04:art:18";

    static final LawSource EMPTY = new LawSource() {
        public Optional<Law> find(String lawId, LocalDate effectiveDate) { return Optional.empty(); }
        public Optional<Law> findBaseline(String lawId) { return Optional.empty(); }
    };

    private final ActionRevisionUseCase useCase = mock(ActionRevisionUseCase.class);
    private final ActionRevisionController controller =
            new ActionRevisionController(useCase, new CitedSourceResolver(EMPTY));

    private static final List<SituationAnswer> ANSWERS = List.of(new SituationAnswer("q1", "예"));

    private static ActionRevisionApiRequest request() {
        return new ActionRevisionApiRequest(new AnalyzeApiRequest.LawRefDto("001809", EF, null), ANSWERS);
    }

    private static RevisionOutcome.Revised revised() {
        ImpactResult action = new ImpactResult("LAW:001809@2026-08-04", "ACTION", "대응 방안", List.of(), List.of(),
                List.of(new ImpactResult.Action("신고한다", "2026-08-04까지", List.of(CITE))),
                null, List.of(), "참고용", null);
        return new RevisionOutcome.Revised(new AnalyzeResponse(action, Set.of(CITE),
                new Situation(List.of(), List.of(new StepNote(0, "이미 계약했다면 해당돼요.")))));
    }

    @Test
    @DisplayName("Revised — action·stepNotes·sources, unmet 없음")
    void 재생성되면_action_stepNotes_sources() {
        when(useCase.revise(any(), any(), any(), any())).thenReturn(revised());

        ResponseEntity<ActionRevisionApiResponse> response = controller.revise(request(), new MockHttpServletRequest());

        assertEquals(200, response.getStatusCode().value());
        ActionRevisionApiResponse body = response.getBody();
        assertEquals("신고한다", body.action().actions().get(0).what());
        assertEquals(List.of(new StepNote(0, "이미 계약했다면 해당돼요.")), body.stepNotes());
        assertEquals(List.of(CITE), body.sources().stream().map(AnalysisApiResponse.CitedSource::sourceId).toList());
        assertNull(body.unmet());
    }

    @Test
    @DisplayName("결과가 담은 모든 인용(대응 basis·주장·영향)이 sources 에 있다 — 근거 없는 칩이 생기지 않는다")
    void 모든_인용이_sources에_있다() {
        String claimCite = "LAW:001809@2026-08-04:art:19";
        String impactCite = "LAW:001809@2026-08-04:amend";
        ImpactResult result = new ImpactResult("LAW:001809@2026-08-04", "ACTION", null,
                List.of(new ImpactResult.Claim("주장", List.of(claimCite), 0.9)),
                List.of(new ImpactResult.Impact("비용", "부담", "자세히", List.of(impactCite))),
                List.of(new ImpactResult.Action("신고한다", null, List.of(CITE))), null, List.of(), "참고용", null);
        when(useCase.revise(any(), any(), any(), any())).thenReturn(new RevisionOutcome.Revised(
                new AnalyzeResponse(result, Set.of(CITE, claimCite, impactCite))));

        ActionRevisionApiResponse body = controller.revise(request(), new MockHttpServletRequest()).getBody();

        assertEquals(List.of(CITE, claimCite, impactCite).stream().sorted().toList(),
                body.sources().stream().map(AnalysisApiResponse.CitedSource::sourceId).sorted().toList());
    }

    @Test
    @DisplayName("NotRevised — unmet 만(action·stepNotes·sources 없음), 200")
    void 못만들면_unmet만() {
        Unmet unmet = new Unmet(Unmet.Reason.DAILY_LIMIT, "오늘 한도를 다 썼어요.");
        when(useCase.revise(any(), any(), any(), any())).thenReturn(new RevisionOutcome.NotRevised(unmet));

        ResponseEntity<ActionRevisionApiResponse> response = controller.revise(request(), new MockHttpServletRequest());

        assertEquals(200, response.getStatusCode().value());
        assertEquals(unmet, response.getBody().unmet());
        assertNull(response.getBody().action());
        assertNull(response.getBody().stepNotes());
        assertNull(response.getBody().sources());
    }

    @Test
    @DisplayName("lawRef 가 없으면 IllegalArgumentException(400 매핑)")
    void lawRef_없으면_예외() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.revise(new ActionRevisionApiRequest(null, ANSWERS), new MockHttpServletRequest()));
        assertThrows(IllegalArgumentException.class,
                () -> controller.revise(new ActionRevisionApiRequest(
                        new AnalyzeApiRequest.LawRefDto("001809", null, null), ANSWERS), new MockHttpServletRequest()));
    }

    @Test
    @DisplayName("userId·도메인 lawRef·답·세션 어댑터를 유스케이스에 넘긴다")
    void 유스케이스에_넘긴다() {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.getSession(true).setAttribute(CurrentUser.SESSION_KEY, userId);
        when(useCase.revise(any(), any(), any(), any())).thenReturn(revised());

        controller.revise(request(), httpRequest);

        verify(useCase).revise(eq(Optional.of(userId)), eq(new LawRef("001809", EF, null)), eq(ANSWERS),
                any(HttpSessionSituationQuestions.class));
    }
}
