package com.lia.core.application.analysis;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import com.lia.core.pipeline.dispatch.DispatchResult;
import com.lia.core.pipeline.dispatch.Unmet;
import com.lia.core.pipeline.plan.AnalysisQuery;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.pipeline.plan.Target;
import com.lia.core.pipeline.resolve.ResolutionResult;
import com.lia.core.pipeline.resolve.ResolutionState;

/** 유스케이스 결과 → {@link QueryEvent}. 응답에 쓰는 값만 읽고 원문·식별자는 보지 않는다. */
final class QueryEvents {

    private QueryEvents() {}

    static QueryEvent analyzed(Instant at, boolean signedIn, boolean profileSearch, String promptVersion,
                               AnalysisOutcome outcome) {
        return switch (outcome) {
            case AnalysisOutcome.Unresolved u -> unresolved(at, signedIn, profileSearch, promptVersion, u);
            case AnalysisOutcome.Analyzed a -> answered(at, signedIn, profileSearch, promptVersion, a);
        };
    }

    private static QueryEvent unresolved(Instant at, boolean signedIn, boolean profileSearch, String promptVersion,
                                         AnalysisOutcome.Unresolved unresolved) {
        ResolutionResult resolution = unresolved.resolution();
        String state = unresolved.offTopic() ? QueryEvent.OFF_TOPIC : resolution.state().name();
        Integer candidates = resolution.state() == ResolutionState.AMBIGUOUS ? resolution.candidates().size() : null;
        return new QueryEvent(at, QueryEvent.Kind.ANALYZE, QueryEvent.Status.UNRESOLVED, signedIn, null, state,
                null, null, null, null, null, candidates, profileSearch, promptVersion);
    }

    private static QueryEvent answered(Instant at, boolean signedIn, boolean profileSearch, String promptVersion,
                                       AnalysisOutcome.Analyzed analyzed) {
        AnalysisQuery query = analyzed.query();
        DispatchResult result = analyzed.result();
        boolean discovery = query.target() instanceof Target.Discovery;
        Integer candidates = result.discovery() == null ? null : result.discovery().found().candidates().size();
        boolean answered = discovery ? candidates != null && candidates > 0 : !result.filled().isEmpty();
        Boolean articleNamed = query.target() instanceof Target.Reference reference
                ? reference.lawRef().articleNo() != null : null;
        return new QueryEvent(at, QueryEvent.Kind.ANALYZE,
                answered ? QueryEvent.Status.ANSWERED : QueryEvent.Status.NOT_ANSWERED, signedIn,
                discovery ? QueryEvent.Route.DISCOVERY : QueryEvent.Route.REFERENCE, null,
                query.primaryType(), query.types(), result.filled().keySet(), reasons(result.unmet()),
                articleNamed, candidates, profileSearch, promptVersion);
    }

    static QueryEvent anonymousLimit(Instant at, boolean profileSearch, String promptVersion) {
        return new QueryEvent(at, QueryEvent.Kind.ANALYZE, QueryEvent.Status.ANONYMOUS_LIMIT, false, null, null,
                null, null, null, null, null, null, profileSearch, promptVersion);
    }

    static QueryEvent revision(Instant at, boolean signedIn, String promptVersion, RevisionOutcome outcome) {
        Set<QueryType> action = Set.of(QueryType.ACTION);
        return switch (outcome) {
            case RevisionOutcome.Revised r -> new QueryEvent(at, QueryEvent.Kind.ACTION_REVISION,
                    QueryEvent.Status.ANSWERED, signedIn, QueryEvent.Route.REFERENCE, null, QueryType.ACTION, action,
                    action, null, null, null, null, promptVersion);
            case RevisionOutcome.NotRevised n -> new QueryEvent(at, QueryEvent.Kind.ACTION_REVISION,
                    QueryEvent.Status.NOT_ANSWERED, signedIn, QueryEvent.Route.REFERENCE, null, QueryType.ACTION, action,
                    null, Map.of(QueryType.ACTION, n.unmet().reason()), null, null, null, promptVersion);
        };
    }

    /** 예외로 끝난 요청 — 결과 모양이 없다. */
    static QueryEvent error(Instant at, QueryEvent.Kind kind, boolean signedIn, Boolean profileSearch,
                            String promptVersion) {
        return new QueryEvent(at, kind, QueryEvent.Status.ERROR, signedIn, null, null, null, null, null, null,
                null, null, profileSearch, promptVersion);
    }

    private static Map<QueryType, Unmet.Reason> reasons(Map<QueryType, Unmet> unmet) {
        Map<QueryType, Unmet.Reason> reasons = new EnumMap<>(QueryType.class);
        unmet.forEach((dimension, u) -> reasons.put(dimension, u.reason()));
        return reasons;
    }
}
