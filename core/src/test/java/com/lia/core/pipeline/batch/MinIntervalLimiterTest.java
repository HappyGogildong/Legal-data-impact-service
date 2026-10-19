package com.lia.core.pipeline.batch;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 최소 호출 간격 — 연속 호출이 간격만큼 벌어진다. */
class MinIntervalLimiterTest {

    @Test
    @DisplayName("간격 100ms 로 세 번 → 최소 200ms 소요, 첫 호출은 즉시")
    void 간격() {
        MinIntervalLimiter limiter = new MinIntervalLimiter(Duration.ofMillis(100));
        long start = System.nanoTime();

        limiter.acquire();
        long afterFirst = System.nanoTime() - start;
        limiter.acquire();
        limiter.acquire();
        long elapsed = System.nanoTime() - start;

        assertTrue(afterFirst < Duration.ofMillis(50).toNanos(), "첫 호출은 기다리지 않는다");
        assertTrue(elapsed >= Duration.ofMillis(200).toNanos(), "세 번이면 간격 두 번");
    }

    @Test
    @DisplayName("간격 0 이면 기다리지 않는다")
    void 간격없음() {
        MinIntervalLimiter limiter = new MinIntervalLimiter(Duration.ZERO);
        long start = System.nanoTime();
        for (int i = 0; i < 100; i++) limiter.acquire();
        assertTrue(System.nanoTime() - start < Duration.ofMillis(100).toNanos());
    }
}
