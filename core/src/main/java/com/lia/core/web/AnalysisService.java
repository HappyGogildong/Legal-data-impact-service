package com.lia.core.web;

import org.springframework.stereotype.Service;

import com.lia.core.pipeline.dispatch.QueryDispatcher;
import com.lia.core.pipeline.plan.LawRef;
import com.lia.core.pipeline.plan.PlanResult;
import com.lia.core.pipeline.plan.QueryPlanner;

/**
 * 온라인 오케스트레이터 — 계획({@link QueryPlanner})과 실행({@link QueryDispatcher})을 잇는 글루.
 * 새 지능이 아니라 배선: {@code plan()} → (Planned면 {@code dispatch()}, Unresolved면 그대로).
 * 웹 프레임워크 타입에 의존하지 않는다(HTTP 매핑은 {@link AnalysisController}).
 *
 * <p><b>{@code profilePresent=false} 고정</b> — UserProfile Store 미구현(#12)이라 이번 증분은
 * Layer A만. Layer B(IMPACT·ACTION) 차원은 dispatcher가 {@code unmet}으로 처리한다.
 */
@Service
public class AnalysisService {

    private final QueryPlanner planner;
    private final QueryDispatcher dispatcher;

    public AnalysisService(QueryPlanner planner, QueryDispatcher dispatcher) {
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
