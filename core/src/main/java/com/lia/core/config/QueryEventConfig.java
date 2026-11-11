package com.lia.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.lia.core.application.analysis.QueryEvent;
import com.lia.core.application.analysis.QueryEventRecorder;
import com.lia.core.observability.RecordWriter;
import com.lia.core.store.QueryEventStore;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * 질의 이벤트 기록 배선(D83) — 스펙: docs/components/observability/QueryEventRecorder.md.
 * 분석·재생성은 웹 요청에서만 일어나므로 늘 비동기로 쓴다(요청 경로에 DB 쓰기를 더하지 않는다).
 */
@Configuration
public class QueryEventConfig {

    /** 질의 이벤트 쓰기 장치 — 닫을 때 대기열을 잠시 비운다. */
    @Bean(destroyMethod = "close")
    public RecordWriter<QueryEvent> queryEventWriter(JdbcClient jdbcClient, MeterRegistry registry,
                                                     @Value("${lia.query-events.queue-capacity}") int queueCapacity) {
        QueryEventStore store = new QueryEventStore(jdbcClient);
        return RecordWriter.async("query_event", store::record, queueCapacity, registry);
    }

    /** 기록 포트 — 응답을 막거나 실패시키지 않는다. */
    @Bean
    public QueryEventRecorder queryEventRecorder(RecordWriter<QueryEvent> queryEventWriter) {
        return queryEventWriter::write;
    }
}
