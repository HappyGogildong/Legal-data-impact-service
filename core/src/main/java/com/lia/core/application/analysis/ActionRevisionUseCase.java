package com.lia.core.application.analysis;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.lia.core.application.profile.PersonalizationGate;
import com.lia.core.domain.analysis.ConfirmedFact;
import com.lia.core.domain.analysis.SituationAnswer;
import com.lia.core.domain.analysis.SituationQuestion;
import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.analyze.AnalysisEngine;
import com.lia.core.pipeline.analyze.AnalyzeRequest;
import com.lia.core.pipeline.analyze.AnalyzeResponse;
import com.lia.core.pipeline.analyze.InsufficientGroundingException;
import com.lia.core.pipeline.dispatch.PersonalAdmission;
import com.lia.core.pipeline.dispatch.PersonalizedHandler;
import com.lia.core.pipeline.dispatch.Personalization;
import com.lia.core.pipeline.dispatch.PersonalizationQuota;
import com.lia.core.pipeline.dispatch.Unmet;
import com.lia.core.pipeline.plan.LawRef;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.store.LawSource;

/**
 * 대응 방안 상황 확인 → 대응 방안만 재생성(D71). 게이트 순서: 개인화 → 세션 질문(없으면 만료) → 답 검증(위조·형식 위반은 400)
 * → 정본 → 기준선·하루 한도({@link PersonalAdmission}, 디스패처와 같은 규칙) → 엔진(ACTION, Opus 1회).
 * Spring 애노테이션 없음 — {@code PipelineConfig} 의 {@code @Bean} 이 배선한다.
 * 익명은 보안 설정이 401 로 막아 여기까지 오지 않는다 — {@code Optional<UUID>} 는 {@link PersonalizationGate#check} 재사용용.
 * 답 내용은 로그에 남기지 않는다.
 */
public class ActionRevisionUseCase {

    private final PersonalizationGate gate;
    private final LawSource laws;
    private final PersonalizationQuota quota;
    private final AnalysisEngine engine;

    public ActionRevisionUseCase(PersonalizationGate gate, LawSource laws, PersonalizationQuota quota,
                                 AnalysisEngine engine) {
        this.gate = gate;
        this.laws = laws;
        this.quota = quota;
        this.engine = engine;
    }

    public RevisionOutcome revise(Optional<UUID> userId, LawRef ref, List<SituationAnswer> answers,
                                  SituationQuestions questions) {
        Personalization personalization = gate.check(userId);
        if (personalization instanceof Personalization.Blocked blocked) {
            return notRevised(new Unmet(blocked.reason(), "개인화 불가 (상황 확인): " + blocked.reason()));
        }
        Personalization.Usable usable = (Personalization.Usable) personalization;

        Optional<List<SituationQuestion>> stored = questions.find(ref);
        if (stored.isEmpty()) {
            return notRevised(new Unmet(Unmet.Reason.SITUATION_EXPIRED, "상황 질문이 만료됨 — 다시 받아야 한다"));
        }
        List<ConfirmedFact> facts = ConfirmedFact.confirm(stored.get(), answers);   // 위조·형식 위반 → 400

        Optional<Law> law = laws.find(ref.lawId(), ref.effectiveDate());
        if (law.isEmpty()) return notRevised(PersonalAdmission.lawNotLoaded(ref.lawId(), ref.effectiveDate()));
        Law baseline = laws.findBaseline(ref.lawId()).orElse(null);
        Optional<Unmet> rejected = PersonalAdmission.admit(usable.userId(), law.get(), baseline, quota);
        if (rejected.isPresent()) return notRevised(rejected.get());

        try {
            AnalyzeResponse response = engine.analyze(new AnalyzeRequest(
                    QueryType.ACTION, law.get(), baseline, usable.profile(), null, facts));
            // 첫 분석(/analyses)과 같은 대응 방안 모양으로 — 요약·주장·영향은 비운다(PersonalizedHandler 와 한 곳)
            return new RevisionOutcome.Revised(new AnalyzeResponse(PersonalizedHandler.actionPart(response.result()),
                    response.injectedSourceIds(), response.situation()));
        } catch (InsufficientGroundingException e) {
            return notRevised(PersonalAdmission.insufficientGrounding(QueryType.ACTION));
        }
    }

    private static RevisionOutcome notRevised(Unmet unmet) {
        return new RevisionOutcome.NotRevised(unmet);
    }
}
