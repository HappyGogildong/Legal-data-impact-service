package com.lia.core.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lia.core.application.analysis.AnalysisOutcome;
import com.lia.core.application.analysis.AnalyzeUseCase;
import com.lia.core.auth.CurrentUser;
import com.lia.core.pipeline.analyze.AnalyzeResponse;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.pipeline.plan.Target;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * {@code POST /api/v1/analyses} — 자연어 질의 분석 진입점([[service-api-spec]] §3.0).
 * <b>HTTP 경계만</b> 담당한다: 요청 검증(빈 query→400) → {@link AnalysisService} 위임 →
 * 응답 매핑은 {@link AnalysisResponseMapper}에 맡기고 상태코드만 얹는다. 해소 4상태·분석은 모두 200
 * (4xx/5xx는 시스템 오류 전용, §4.1).
 */
@RestController
@RequestMapping("/api/v1/analyses")
public class AnalysisController {

    private final AnalyzeUseCase useCase;
    private final AnalysisResponseMapper mapper;

    public AnalysisController(AnalyzeUseCase useCase, AnalysisResponseMapper mapper) {
        this.useCase = useCase;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<AnalysisApiResponse> analyze(@RequestBody AnalyzeApiRequest req, HttpServletRequest request) {
        if (req == null || req.query() == null || req.query().isBlank()) {
            throw new IllegalArgumentException("query는 필수입니다.");
        }
        // 익명 허용 — 세션이 있으면 그 userId 로 개인화(Layer B) 판정(D69). 본문으로 프로필을 받지 않는다.
        AnalysisOutcome outcome = useCase.analyze(req.query().strip(), req.explicitRef(), CurrentUser.userId(request),
                req.wantsProfileSearch());
        rememberSituationQuestions(outcome, request);
        return ResponseEntity.ok(mapper.toBody(outcome)); // 해소 결과·분석 모두 200
    }

    /** 대응 방안과 함께 나온 상황 질문을 세션(메모리)에 둔다 — 재생성 요청은 questionId 만 보낸다(D71). 질문이 없으면 기존 것 유지. */
    private static void rememberSituationQuestions(AnalysisOutcome outcome, HttpServletRequest request) {
        if (!(outcome instanceof AnalysisOutcome.Analyzed a)) return;
        if (!(a.query().target() instanceof Target.Reference ref)) return;
        AnalyzeResponse action = a.result().filled().get(QueryType.ACTION);
        if (action == null || action.situation().questions().isEmpty()) return;
        HttpSession session = request.getSession(false);   // Layer B 가 채워졌다면 로그인 세션이 있다
        if (session == null) return;
        new HttpSessionSituationQuestions(session).save(ref.lawRef(), action.situation().questions());
    }
}
