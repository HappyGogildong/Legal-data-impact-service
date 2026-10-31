package com.lia.core.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.lia.core.application.analysis.ActionRevisionUseCase;
import com.lia.core.application.analysis.RevisionOutcome;
import com.lia.core.auth.CurrentUser;
import com.lia.core.domain.analysis.SituationAnswer;
import com.lia.core.domain.analysis.SituationQuestion;
import com.lia.core.pipeline.plan.LawRef;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/v1/analyses/action-revisions} — 상황 질문에 답하면 대응 방안만 다시 만든다(D71).
 * 로그인 필수·CSRF 보호(SecurityConfig: anyRequest authenticated, CSRF 예외는 /analyses 만). 미충족은 200 {@code unmet}.
 * 답 내용은 로그에 남기지 않는다.
 */
@RestController
@RequestMapping("/api/v1/analyses/action-revisions")
public class ActionRevisionController {

    private final ActionRevisionUseCase useCase;
    private final CitedSourceResolver sources;

    public ActionRevisionController(ActionRevisionUseCase useCase, CitedSourceResolver sources) {
        this.useCase = useCase;
        this.sources = sources;
    }

    @PostMapping
    public ResponseEntity<ActionRevisionApiResponse> revise(@RequestBody ActionRevisionApiRequest req,
                                                            HttpServletRequest request) {
        if (req == null || req.lawRef() == null || req.lawRef().lawId() == null || req.lawRef().effectiveDate() == null) {
            throw new IllegalArgumentException("lawRef(lawId·effectiveDate)는 필수입니다.");
        }
        if (req.setId() == null || req.setId().isBlank()) {
            throw new IllegalArgumentException("setId(질문 묶음 식별자)는 필수입니다.");
        }
        RevisionOutcome outcome = useCase.revise(CurrentUser.userId(request), req.lawRef().toDomain(), req.setId(), req.answers(),
                new HttpSessionSituationQuestions(request.getSession()));
        ActionRevisionApiResponse body = switch (outcome) {
            case RevisionOutcome.Revised r -> ActionRevisionApiResponse.revised(r.response().result(),
                    r.response().situation().stepNotes(),
                    sources.resolve(AnalysisResponseMapper.citations(List.of(r.response().result()))));
            case RevisionOutcome.NotRevised n -> ActionRevisionApiResponse.notRevised(n.unmet());
        };
        if (outcome instanceof RevisionOutcome.Revised && req.analysisId() != null) {
            CurrentUser.userId(request).ifPresent(userId -> attachToRecentAnalysis(req, userId, body, request.getSession()));
        }
        return ResponseEntity.ok(body);
    }

    /**
     * 재생성본과 실제로 쓴 답(질문 문장과 짝)을 그 사용자의 분석 결과에 붙인다(D73) — 이력 저장이 함께 담는다.
     * 같은 법령이고 요청의 질문 묶음(setId)이 그 분석의 것과 같을 때만 붙는다. 답 내용은 로그에 남기지 않는다.
     */
    private static void attachToRecentAnalysis(ActionRevisionApiRequest req, UUID userId, ActionRevisionApiResponse body,
                                               HttpSession session) {
        LawRef lawRef = req.lawRef().toDomain();
        Map<String, String> questionTexts = new HttpSessionSituationQuestions(session).find(lawRef).orElse(List.of()).stream()
                .collect(Collectors.toMap(SituationQuestion::id, SituationQuestion::text, (a, b) -> a));
        List<HttpSessionRecentAnalyses.AnsweredQuestion> answered = (req.answers() == null ? List.<SituationAnswer>of() : req.answers())
                .stream()
                .filter(answer -> questionTexts.containsKey(answer.questionId()))
                .map(answer -> new HttpSessionRecentAnalyses.AnsweredQuestion(questionTexts.get(answer.questionId()), answer.answer()))
                .toList();
        new HttpSessionRecentAnalyses(session).attachRevision(req.analysisId(), userId, lawRef, req.setId(), body, answered);
    }
}
