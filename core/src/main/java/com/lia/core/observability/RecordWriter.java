package com.lia.core.observability;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * 관측 기록(호출 기록·질의 이벤트)을 본 처리에서 떼어 내는 쓰기 장치(D83) — 쓰기 실패·대기열 초과를 삼키고
 * {@code lia.records.dropped{record, reason}} 를 올린다. 웹은 비동기(단일 스레드·크기 제한 대기열),
 * 한 번 돌고 끝나는 배치·평가는 동기다. 닫을 때 남은 기록을 잠시 비운다. 로그에는 행 내용을 남기지 않는다.
 *
 * @param <T> 기록 한 건
 */
public final class RecordWriter<T> implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RecordWriter.class);
    private static final long DRAIN_SECONDS = 5;

    private final String record;
    private final Consumer<T> store;
    private final MeterRegistry registry;
    private final ThreadPoolExecutor executor;   // null 이면 동기

    private RecordWriter(String record, Consumer<T> store, MeterRegistry registry, ThreadPoolExecutor executor) {
        this.record = record;
        this.store = store;
        this.registry = registry;
        this.executor = executor;
    }

    /** 호출 스레드에서 바로 쓴다(배치·평가). @param record 지표 태그용 기록 종류(예: {@code llm_call}) */
    public static <T> RecordWriter<T> sync(String record, Consumer<T> store, MeterRegistry registry) {
        return new RecordWriter<>(record, store, registry, null);
    }

    /** 단일 스레드가 크기 {@code capacity} 대기열에서 꺼내 쓴다(웹). 넘치면 버린다. */
    public static <T> RecordWriter<T> async(String record, Consumer<T> store, int capacity, MeterRegistry registry) {
        if (capacity < 1) throw new IllegalArgumentException("기록 대기열 크기는 1 이상: " + capacity);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacity), runnable -> {
                    Thread thread = new Thread(runnable, "record-writer-" + record);
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        return new RecordWriter<>(record, store, registry, executor);
    }

    public void write(T row) {
        if (executor == null) {
            store(row);
            return;
        }
        try {
            executor.execute(() -> store(row));
        } catch (RejectedExecutionException full) {
            dropped("queue_full");
        }
    }

    private void store(T row) {
        try {
            store.accept(row);
        } catch (RuntimeException e) {
            log.warn("관측 기록 실패 — record={}: {}", record, e.toString());
            dropped("write_failed");
        }
    }

    private void dropped(String reason) {
        registry.counter(Obs.RECORDS_DROPPED, Obs.TAG_RECORD, record, Obs.TAG_REASON, reason).increment();
    }

    /** 새 기록을 받지 않고, 남은 기록을 최대 몇 초 비운다. */
    @Override
    public void close() {
        if (executor == null) return;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(DRAIN_SECONDS, TimeUnit.SECONDS)) {
                log.warn("관측 기록 대기열({})을 {}초 안에 비우지 못했다 — 남은 {}건을 버린다",
                        record, DRAIN_SECONDS, executor.shutdownNow().size());
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
