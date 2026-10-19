package com.lia.core.pipeline.batch;

import java.time.LocalDate;

import com.lia.core.pipeline.connector.PendingLawFeed;
import com.lia.core.pipeline.connector.RawLaw;

/** 국가법령정보 호출 간격 제한 데코레이터(D62) — 워커 수와 무관하게 API 로 나가는 속도를 묶는다. */
public class RateLimitedLawFeed implements PendingLawFeed {

    private final PendingLawFeed delegate;
    private final MinIntervalLimiter limiter;

    public RateLimitedLawFeed(PendingLawFeed delegate, MinIntervalLimiter limiter) {
        this.delegate = delegate;
        this.limiter = limiter;
    }

    @Override
    public PendingList listAllPending(LocalDate from, LocalDate to) {
        // ponytail: 목록은 내부적으로 여러 페이지를 부르지만 실행당 한 번이라 한 번만 잰다.
        limiter.acquire();
        return delegate.listAllPending(from, to);
    }

    @Override
    public RawLaw fetchPending(String mst, LocalDate effectiveDate) {
        limiter.acquire();
        return delegate.fetchPending(mst, effectiveDate);
    }

    @Override
    public RawLaw fetchCurrent(String lawId) {
        limiter.acquire();
        return delegate.fetchCurrent(lawId);
    }
}
