package com.lia.core.application.analysis;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.lia.core.pipeline.dispatch.Unmet;
import com.lia.core.pipeline.plan.QueryType;

/**
 * {@code query_events} 한 행 — 요청 1건의 결과 모양(D83). 질문 원문·{@code user_id}·프로필·법령ID 는 담지 않는다.
 * 스펙: docs/components/observability/QueryEventRecorder.md.
 *
 * @param resolutionState 미해소 사유({@code OFF_TOPIC} 또는 해소 4상태 이름), 그 밖엔 null
 * @param articleNamed    법령 지목 경로에서 조문을 지목했나(그 밖엔 null)
 * @param candidates      발견 후보 수 또는 비슷한 이름 후보 수(그 밖엔 null)
 * @param profileSearch   "내 프로필 기준으로 찾기"(첫 분석만, 재생성은 null)
 */
public record QueryEvent(Instant occurredAt, Kind kind, Status status, boolean signedIn, Route route,
                         String resolutionState, QueryType primaryType, Set<QueryType> types, Set<QueryType> filled,
                         Map<QueryType, Unmet.Reason> unmet, Boolean articleNamed, Integer candidates,
                         Boolean profileSearch, String promptVersion) {

    public enum Kind { ANALYZE, ACTION_REVISION }

    public enum Status { ANSWERED, NOT_ANSWERED, UNRESOLVED, ANONYMOUS_LIMIT, ERROR }

    public enum Route { REFERENCE, DISCOVERY }

    /** 법령 질문이 아니어서 거부한 미해소 — 해소 4상태와 구분해 센다. */
    public static final String OFF_TOPIC = "OFF_TOPIC";

    public QueryEvent {
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(status, "status");
        types = types == null || types.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(types));
        filled = filled == null || filled.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(filled));
        unmet = unmet == null || unmet.isEmpty() ? Map.of() : Map.copyOf(new EnumMap<>(unmet));
    }
}
