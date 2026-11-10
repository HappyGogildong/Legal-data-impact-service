package com.lia.core.observability.llm;

import org.springframework.jdbc.core.simple.JdbcClient;

/** {@code llm_calls}(V16) 에 한 행 INSERT. 실패는 그대로 던진다 — 삼키는 건 {@link LlmCallWriter} 몫. */
public class JdbcLlmCallRecorder implements LlmCallRecorder {

    private final JdbcClient jdbc;

    public JdbcLlmCallRecorder(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(LlmCall call) {
    }
}
