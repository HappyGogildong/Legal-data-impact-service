package com.lia.core.application.analysis;

import com.lia.core.pipeline.dispatch.QueryDispatcher;
import com.lia.core.pipeline.plan.LawRef;
import com.lia.core.pipeline.plan.PlanResult;
import com.lia.core.pipeline.plan.QueryPlanner;

/**
 * 온라인 분석 유스케이스 — 계획({@link QueryPlanner})과 실행({@link QueryDispatcher})을 조합해
 * "자연어 질의 → 그라운딩 답"을 완성한다. 새 지능이 아니라 <b>블록 조합</b>: {@code plan()} →
 * (Planned면 {@code dispatch()}, Unresolved면 그대로).
 *
 * <p><b>프레임워크 비의존</b> — Spring 애노테이션이 없다(배선은 {@code PipelineConfig}의 {@code @Bean}).
 * application 코어는 자기가 Spring 위에서 도는지 몰라야 한다. 흔한 CRUD {@code @Service}가 아니라
 * <b>유스케이스</b>라 이름·계층을 구분한다.
 *
 * <p><b>{@code profilePresent=false} 고정</b> — UserProfile Store 미구현(#12)이라 이번 증분은
 * Layer A만. Layer B(IMPACT·ACTION) 차원은 dispatcher가 {@code unmet}으로 처리한다.
 */
public class AnalyzeUseCase {

    private final QueryPlanner planner;
    private final QueryDispatcher dispatcher;

    public AnalyzeUseCase(QueryPlanner planner, QueryDispatcher dispatcher) {
        this.planner = planner;
        this.dispatcher = dispatcher;
    }

    /** 자연어 질의 → 분석 결과. 미해소는 분석으로 새지 않는다(fail-closed, plan 게이트가 강제). */
    public AnalysisOutcome analyze(String query, LawRef explicitRef) {
        PlanResult plan = planner.plan(query, explicitRef, false); // 프로필 미도입(#12) → Layer A
        return switch (plan) {
            case PlanResult.Unresolved u -> new AnalysisOutcome.Unresolved(u.resolution());
            case PlanResult.Planned p -> new AnalysisOutcome.Analyzed(p.query(), dispatcher.dispatch(p.query()));
        };
    }
}
