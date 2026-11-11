package com.lia.core.application.analysis;

import java.util.List;

import com.lia.core.pipeline.connector.RawLaw;
import com.lia.core.pipeline.dispatch.DispatchResult;
import com.lia.core.pipeline.plan.AnalysisQuery;
import com.lia.core.pipeline.resolve.ResolutionResult;

/**
 * 온라인 분석 유스케이스 결과 — {@code Analyzed}(해소·디스패치 완료) | {@code Unresolved}(4상태 거부, fail-closed).
 * sealed라 소비자(web 매퍼)가 두 경우를 빠짐없이 처리한다. <b>프레임워크·HTTP 타입 비의존</b>(application 계층).
 */
public sealed interface AnalysisOutcome permits AnalysisOutcome.Analyzed, AnalysisOutcome.Unresolved {

    /** @param alternatives 같은 법령의 다른 시행예정본(D43) — 응답의 "다른 시행일 보기" */
    record Analyzed(AnalysisQuery query, DispatchResult result, List<RawLaw> alternatives) implements AnalysisOutcome {
        public Analyzed {
            alternatives = List.copyOf(alternatives);
        }

        public Analyzed(AnalysisQuery query, DispatchResult result) {
            this(query, result, List.of());
        }
    }

    /** @param offTopic 법령 질문이 아니어서 거부했다(응답 모양은 같다 — 운영 집계용, D83) */
    record Unresolved(ResolutionResult resolution, boolean offTopic) implements AnalysisOutcome {
        public Unresolved(ResolutionResult resolution) {
            this(resolution, false);
        }
    }
}
