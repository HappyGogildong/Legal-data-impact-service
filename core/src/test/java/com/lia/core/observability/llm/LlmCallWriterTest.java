package com.lia.core.observability.llm;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.lia.core.observability.Obs;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** LlmCallWriter — 기록이 본 처리를 막거나 실패시키지 않는다. 버린 건수는 지표로 남는다. */
class LlmCallWriterTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final List<LlmCall> stored = new CopyOnWriteArrayList<>();

    private static LlmCall call() {
        return LlmCall.single(Instant.now(), LlmPurpose.CLASSIFY, "claude-haiku-4-5-20251001", true, LlmUsage.NONE);
    }

    private double dropped(String reason) {
        var counter = registry.find(Obs.LLM_CALLS_DROPPED).tag(Obs.TAG_REASON, reason).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    void 동기_쓰기_실패는_삼키고_센다() {
        LlmCallWriter writer = LlmCallWriter.sync(c -> { throw new IllegalStateException("DB 끊김"); }, registry);

        assertDoesNotThrow(() -> writer.record(call()));

        assertEquals(1, dropped("write_failed"));
    }

    @Test
    void 비동기는_닫을_때_남은_기록을_비운다() {
        LlmCallWriter writer = LlmCallWriter.async(stored::add, 10, registry);
        for (int i = 0; i < 5; i++) writer.record(call());

        writer.close();

        assertEquals(5, stored.size());
    }

    @Test
    void 비동기_대기열이_차면_버리고_센다() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch writing = new CountDownLatch(1);
        LlmCallWriter writer = LlmCallWriter.async(c -> {
            writing.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            stored.add(c);
        }, 1, registry);

        writer.record(call());                       // 쓰는 스레드가 잡고 있다
        assertTrue(writing.await(5, TimeUnit.SECONDS));
        writer.record(call());                       // 대기열 1칸
        writer.record(call());                       // 넘침 — 버린다
        release.countDown();
        writer.close();

        assertEquals(2, stored.size());
        assertEquals(1, dropped("queue_full"));
    }

    @Test
    void 비동기_쓰기_실패도_삼키고_센다() {
        LlmCallWriter writer = LlmCallWriter.async(c -> { throw new IllegalStateException("DB 끊김"); }, 10, registry);

        writer.record(call());
        writer.close();

        assertEquals(1, dropped("write_failed"));
    }
}
