package com.lia.core.application.analysis;

import java.time.Instant;

/** 유스케이스 결과 → {@link QueryEvent}. 응답에 쓰는 값만 읽고 원문·식별자는 보지 않는다. */
final class QueryEvents {

    private QueryEvents() {}

    static QueryEvent analyzed(Instant at, boolean signedIn, boolean profileSearch, String promptVersion,
                               AnalysisOutcome outcome) {
        return error(at, QueryEvent.Kind.ANALYZE, signedIn, profileSearch, promptVersion);
    }

    static QueryEvent anonymousLimit(Instant at, boolean profileSearch, String promptVersion) {
        return error(at, QueryEvent.Kind.ANALYZE, false, profileSearch, promptVersion);
    }

    static QueryEvent revision(Instant at, boolean signedIn, String promptVersion, RevisionOutcome outcome) {
        return error(at, QueryEvent.Kind.ACTION_REVISION, signedIn, null, promptVersion);
    }

    /** 예외로 끝난 요청. */
    static QueryEvent error(Instant at, QueryEvent.Kind kind, boolean signedIn, Boolean profileSearch,
                            String promptVersion) {
        return new QueryEvent(at, kind, QueryEvent.Status.ERROR, signedIn, null, null, null, null, null, null,
                null, null, profileSearch, promptVersion);
    }
}
