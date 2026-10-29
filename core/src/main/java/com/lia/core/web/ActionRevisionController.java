package com.lia.core.web;

import java.util.List;

import com.lia.core.application.analysis.ActionRevisionUseCase;
import com.lia.core.application.analysis.RevisionOutcome;
import com.lia.core.auth.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
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
        return ResponseEntity.ok(switch (outcome) {
            case RevisionOutcome.Revised r -> ActionRevisionApiResponse.revised(r.response().result(),
                    r.response().situation().stepNotes(),
                    sources.resolve(AnalysisResponseMapper.citations(List.of(r.response().result()))));
            case RevisionOutcome.NotRevised n -> ActionRevisionApiResponse.notRevised(n.unmet());
        });
    }
}
