package com.lia.core.store;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.lia.core.application.analysis.QueryEvent;
import com.lia.core.application.analysis.QueryEventRecorder;
import com.lia.core.pipeline.dispatch.Unmet;
import com.lia.core.pipeline.plan.QueryType;

/** {@code query_events}(V17) 에 한 행 INSERT. 실패는 그대로 던진다 — 삼키는 건 {@code RecordWriter} 몫. */
public class QueryEventStore implements QueryEventRecorder {

    private final JdbcClient jdbc;

    public QueryEventStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(QueryEvent event) {
        jdbc.sql("""
                INSERT INTO query_events (occurred_at, kind, status, signed_in, route, resolution_state, primary_type,
                                          types, filled, unmet, article_named, candidates, profile_search, prompt_version)
                VALUES (:occurredAt, :kind, :status, :signedIn, :route, :resolutionState, :primaryType,
                        :types, :filled, CAST(:unmet AS jsonb), :articleNamed, :candidates, :profileSearch, :promptVersion)
                """)
                .param("occurredAt", OffsetDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC))
                .param("kind", event.kind().name())
                .param("status", event.status().name())
                .param("signedIn", event.signedIn())
                .param("route", event.route() == null ? null : event.route().name())
                .param("resolutionState", event.resolutionState())
                .param("primaryType", event.primaryType() == null ? null : event.primaryType().name())
                .param("types", list(event.types()))
                .param("filled", list(event.filled()))
                .param("unmet", json(event.unmet()))
                .param("articleNamed", event.articleNamed())
                .param("candidates", event.candidates())
                .param("profileSearch", event.profileSearch())
                .param("promptVersion", event.promptVersion())
                .update();
    }

    /** QueryType 선언 순서로 쉼표 구분 — 빈 목록은 NULL. */
    static String list(Set<QueryType> dimensions) {
        if (dimensions.isEmpty()) return null;
        return dimensions.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }

    /** 차원·사유는 enum 이름뿐이라 이스케이프할 문자가 없다. */
    static String json(Map<QueryType, Unmet.Reason> unmet) {
        return new TreeMap<>(unmet).entrySet().stream()
                .map(e -> "\"" + e.getKey().name() + "\":\"" + e.getValue().name() + "\"")
                .collect(Collectors.joining(",", "{", "}"));
    }
}
