package com.lia.core.observability.llm;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * 기록을 본 처리에서 떼어 내는 겉 구현 — 쓰기 실패·대기열 초과를 삼키고
 * {@code lia.llm.calls.dropped{reason}} 를 올린다. 웹은 비동기(단일 스레드·크기 제한 대기열),
 * 한 번 돌고 끝나는 배치·평가는 동기다. 닫을 때 남은 기록을 잠시 비운다.
 */
public final class LlmCallWriter implements LlmCallRecorder, AutoCloseable {

    private final LlmCallRecorder store;
    private final MeterRegistry registry;

    private LlmCallWriter(LlmCallRecorder store, MeterRegistry registry) {
        this.store = store;
        this.registry = registry;
    }

    /** 호출 스레드에서 바로 쓴다(배치·평가). */
    public static LlmCallWriter sync(LlmCallRecorder store, MeterRegistry registry) {
        return new LlmCallWriter(store, registry);
    }

    /** 단일 스레드가 크기 {@code capacity} 대기열에서 꺼내 쓴다(웹). 넘치면 버린다. */
    public static LlmCallWriter async(LlmCallRecorder store, int capacity, MeterRegistry registry) {
        return new LlmCallWriter(store, registry);
    }

    @Override
    public void record(LlmCall call) {
        store.record(call);
    }

    @Override
    public void close() {
    }
}
