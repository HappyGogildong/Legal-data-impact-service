package com.lia.core.observability.llm;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.simple.JdbcClient;

/** {@code llm_calls}(V16) 에 한 행 INSERT. 실패는 그대로 던진다 — 삼키는 건 {@link com.lia.core.observability.RecordWriter} 몫. */
public class JdbcLlmCallRecorder implements LlmCallRecorder {

    private final JdbcClient jdbc;

    public JdbcLlmCallRecorder(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(LlmCall call) {
        LlmUsage usage = call.usage();
        jdbc.sql("""
                INSERT INTO llm_calls (called_at, purpose, model, prompt_version, group_key, attempt, accepted,
                                       succeeded, requested, input_tokens, output_tokens, cache_read_tokens,
                                       cache_write_tokens, stop_reason, context_sources)
                VALUES (:calledAt, :purpose, :model, :promptVersion, :groupKey, :attempt, :accepted,
                        :succeeded, :requested, :inputTokens, :outputTokens, :cacheReadTokens,
                        :cacheWriteTokens, :stopReason, :contextSources)
                """)
                .param("calledAt", OffsetDateTime.ofInstant(call.calledAt(), ZoneOffset.UTC))
                .param("purpose", call.purpose().name())
                .param("model", call.model())
                .param("promptVersion", call.promptVersion())
                .param("groupKey", call.groupKey())
                .param("attempt", call.attempt())
                .param("accepted", call.accepted())
                .param("succeeded", call.succeeded())
                .param("requested", call.requested())
                .param("inputTokens", usage.inputTokens())
                .param("outputTokens", usage.outputTokens())
                .param("cacheReadTokens", usage.cacheReadTokens())
                .param("cacheWriteTokens", usage.cacheWriteTokens())
                .param("stopReason", usage.stopReason())
                .param("contextSources", call.contextSources())
                .update();
    }
}
