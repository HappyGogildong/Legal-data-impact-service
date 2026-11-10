package com.lia.core.observability.llm;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lia.core.observability.Obs;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * 기록을 본 처리에서 떼어 내는 겉 구현 — 쓰기 실패·대기열 초과를 삼키고
 * {@code lia.llm.calls.dropped{reason}} 를 올린다. 웹은 비동기(단일 스레드·크기 제한 대기열),
 * 한 번 돌고 끝나는 배치·평가는 동기다. 닫을 때 남은 기록을 잠시 비운다.
 */
public final class LlmCallWriter implements LlmCallRecorder, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LlmCallWriter.class);
    private static final long DRAIN_SECONDS = 5;

    private final LlmCallRecorder store;
    private final MeterRegistry registry;
    private final ThreadPoolExecutor executor;   // null 이면 동기

    private LlmCallWriter(LlmCallRecorder store, MeterRegistry registry, ThreadPoolExecutor executor) {
        this.store = store;
        this.registry = registry;
        this.executor = executor;
    }

    /** 호출 스레드에서 바로 쓴다(배치·평가). */
    public static LlmCallWriter sync(LlmCallRecorder store, MeterRegistry registry) {
        return new LlmCallWriter(store, registry, null);
    }

    /** 단일 스레드가 크기 {@code capacity} 대기열에서 꺼내 쓴다(웹). 넘치면 버린다. */
    public static LlmCallWriter async(LlmCallRecorder store, int capacity, MeterRegistry registry) {
        if (capacity < 1) throw new IllegalArgumentException("대기열 크기(lia.llm-calls.queue-capacity)는 1 이상: " + capacity);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacity), runnable -> {
                    Thread thread = new Thread(runnable, "llm-call-writer");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        return new LlmCallWriter(store, registry, executor);
    }

    @Override
    public void record(LlmCall call) {
        if (executor == null) {
            write(call);
            return;
        }
        try {
            executor.execute(() -> write(call));
        } catch (RejectedExecutionException full) {
            dropped("queue_full");
        }
    }

    private void write(LlmCall call) {
        try {
            store.record(call);
        } catch (RuntimeException e) {
            log.warn("호출 기록 실패 — purpose={} model={}: {}", call.purpose(), call.model(), e.toString());
            dropped("write_failed");
        }
    }

    private void dropped(String reason) {
        registry.counter(Obs.LLM_CALLS_DROPPED, Obs.TAG_REASON, reason).increment();
    }

    /** 새 기록을 받지 않고, 남은 기록을 최대 몇 초 비운다. */
    @Override
    public void close() {
        if (executor == null) return;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(DRAIN_SECONDS, TimeUnit.SECONDS)) {
                log.warn("호출 기록 대기열을 {}초 안에 비우지 못했다 — 남은 {}건을 버린다",
                        DRAIN_SECONDS, executor.shutdownNow().size());
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
