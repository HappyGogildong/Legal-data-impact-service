package com.lia.core.pipeline.batch;

import java.time.Duration;

/**
 * 최소 호출 간격 제한(D62) — 워커가 여럿이어도 같은 외부 API 로 나가는 속도를 묶는다.
 * 다음 호출 가능 시각을 하나 들고 순서대로 배정한다.
 */
public class MinIntervalLimiter {

    private final long intervalNanos;
    private long nextSlot = Long.MIN_VALUE;   // System.nanoTime 기준(음수일 수 있어 MIN_VALUE 로 시작)

    public MinIntervalLimiter(Duration interval) {
        this.intervalNanos = interval.toNanos();
    }

    public void acquire() {
        long waitNanos;
        synchronized (this) {
            long now = System.nanoTime();
            long slot = Math.max(now, nextSlot);
            nextSlot = slot + intervalNanos;
            waitNanos = slot - now;
        }
        if (waitNanos > 0) {
            try {
                Thread.sleep(Duration.ofNanos(waitNanos));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("호출 간격 대기 중 중단", e);
            }
        }
    }
}
