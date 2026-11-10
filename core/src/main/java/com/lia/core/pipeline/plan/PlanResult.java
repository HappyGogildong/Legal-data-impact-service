package com.lia.core.pipeline.plan;

import java.util.List;

import com.lia.core.pipeline.connector.RawLaw;
import com.lia.core.pipeline.resolve.ResolutionResult;

/**
 * 계획 결과 — {@code Planned}(검증된 실행 질의) | {@code Unresolved}(4상태 거부, fail-closed).
 * sealed라 소비자(dispatcher/컨트롤러)가 두 경우를 빠짐없이 처리한다.
 */
public sealed interface PlanResult permits PlanResult.Planned, PlanResult.Unresolved {

    /**
     * @param alternatives 해소된 법령의 다른 시행예정본(D43 안내 — "다른 시행일 보기"). 명시적 참조·발견이면 빈 목록
     */
    record Planned(AnalysisQuery query, List<RawLaw> alternatives) implements PlanResult {
        public Planned {
            alternatives = List.copyOf(alternatives);
        }

        public Planned(AnalysisQuery query) {
            this(query, List.of());
        }
    }

    /**
     * 미해소·비법령 — 지어내지 않는다. {@link ResolutionResult}의 4상태·안내를 그대로 전달.
     *
     * @param offTopic 법령 질문이 아니어서 거부했다(상태는 UNVERIFIED 로 같다 — 운영 집계만 이 값으로 가른다, D83)
     */
    record Unresolved(ResolutionResult resolution, boolean offTopic) implements PlanResult {
        public Unresolved(ResolutionResult resolution) {
            this(resolution, false);
        }
    }
}
