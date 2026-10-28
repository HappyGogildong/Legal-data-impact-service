package com.lia.core.application.analysis;

import java.util.Optional;
import java.util.UUID;

import com.lia.core.application.profile.PersonalizationGate;
import com.lia.core.pipeline.dispatch.Personalization;
import com.lia.core.pipeline.dispatch.QueryDispatcher;
import com.lia.core.pipeline.plan.LawRef;
import com.lia.core.pipeline.plan.PlanResult;
import com.lia.core.pipeline.plan.QueryPlanner;
import com.lia.core.pipeline.plan.Target;
import com.lia.core.store.LawViews;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 온라인 분석 유스케이스 — 계획({@link QueryPlanner})과 실행({@link QueryDispatcher})을 조합해
 * "자연어 질의 → 그라운딩 답"을 완성한다. 새 지능이 아니라 <b>블록 조합</b>: {@code plan()} →
 * (Planned면 {@code dispatch()}, Unresolved면 그대로).
 *
 * <p><b>프레임워크 비의존</b> — Spring 애노테이션이 없다(배선은 {@code PipelineConfig}의 {@code @Bean}).
 * application 코어는 자기가 Spring 위에서 도는지 몰라야 한다. 흔한 CRUD {@code @Service}가 아니라
 * <b>유스케이스</b>라 이름·계층을 구분한다.
 *
 * <p><b>개인화(D69)</b> — 세션 userId 로 {@link PersonalizationGate}가 Layer B 가능 여부를 판정하고,
 * 그 판정과 질문 원문을 디스패처에 넘긴다(프롬프트엔 프로필 속성·질문만, userId 는 한도 키로만).
 *
 * <p><b>조회 집계(D66)</b> — Reference 분석이 정본으로 답(채운 차원)을 냈으면 {@link LawViews}로 1회 센다.
 * 기록 실패는 삼키고 로그만 — 분석 응답을 바꾸지 않는다.
 */
public class AnalyzeUseCase {

    private static final Logger log = LoggerFactory.getLogger(AnalyzeUseCase.class);

    private final QueryPlanner planner;
    private final QueryDispatcher dispatcher;
    private final LawViews views;
    private final PersonalizationGate gate;

    public AnalyzeUseCase(QueryPlanner planner, QueryDispatcher dispatcher, LawViews views,
                          PersonalizationGate gate) {
        this.planner = planner;
        this.dispatcher = dispatcher;
        this.views = views;
        this.gate = gate;
    }

    /** 자연어 질의 → 분석 결과. 미해소는 분석으로 새지 않는다(fail-closed, plan 게이트가 강제). */
    public AnalysisOutcome analyze(String query, LawRef explicitRef, Optional<UUID> userId) {
        Personalization personalization = gate.check(userId);
        boolean profilePresent = personalization instanceof Personalization.Usable;
        PlanResult plan = planner.plan(query, explicitRef, profilePresent);
        return switch (plan) {
            case PlanResult.Unresolved u -> new AnalysisOutcome.Unresolved(u.resolution());
            case PlanResult.Planned p -> {
                AnalysisOutcome.Analyzed analyzed = new AnalysisOutcome.Analyzed(
                        p.query(), dispatcher.dispatch(p.query(), query, personalization), p.alternatives());
                countView(analyzed);
                yield analyzed;
            }
        };
    }

    /** 정본으로 답을 낸 Reference 분석만 조회 1회(D66). 실패해도 분석은 그대로. */
    private void countView(AnalysisOutcome.Analyzed analyzed) {
        if (!(analyzed.query().target() instanceof Target.Reference reference)) return;
        if (analyzed.result().filled().isEmpty()) return;   // 정본 미적재 등으로 아무 차원도 못 채움
        LawRef ref = reference.lawRef();
        try {
            views.record(ref.lawId(), ref.effectiveDate());
        } catch (RuntimeException e) {
            log.warn("조회 집계 실패 — 분석 응답에는 영향 없음: {}", e.toString());
        }
    }
}
