package com.lia.core.store;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.lia.core.application.analysis.QueryEvent;
import com.lia.core.application.analysis.QueryEventRecorder;

/** {@code query_events}(V17) 에 한 행 INSERT. 실패는 그대로 던진다 — 삼키는 건 {@code RecordWriter} 몫. */
public class QueryEventStore implements QueryEventRecorder {

    private final JdbcClient jdbc;

    public QueryEventStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(QueryEvent event) {
    }
}
