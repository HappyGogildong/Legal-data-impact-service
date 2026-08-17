package com.lia.core.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 온라인 경로(dispatch·AnalysisEngine) 계측 <b>hook 파사드</b>(D48).
 *
 * <p>비용·캐시·스탬피드 지표는 온라인 답변 경로가 landing할 때 발화한다. 지금은 <b>배선만</b> —
 * 지표명을 {@link Obs} 한곳에 고정해, 코드가 들어오면 이 파사드만 호출하면 대시보드가 채워진다.
 *
 * <p>오프라인 파이프라인 단계(normalize/diff/…)는 이 파사드가 아니라
 * {@code ObservationRegistry} 로 직접 감싼다 — <i>타이머 + span</i> 을 함께 얻기 위해서다.
 */
@Component
public class LiaMetrics {

    private final MeterRegistry registry;

    public LiaMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Opus/Haiku 호출량 — 비용 동인(ADR-001). */
    public void llmCall(String model, String dimension) {
        registry.counter(Obs.LLM_CALLS, Obs.TAG_MODEL, model, Obs.TAG_DIMENSION, dimension).increment();
    }

    /** 캐시 적중 — 스탬피드 판단. */
    public void cacheHit(String layer, String dimension) {
        registry.counter(Obs.CACHE_HIT, Obs.TAG_LAYER, layer, Obs.TAG_DIMENSION, dimension).increment();
    }

    public void cacheMiss(String layer, String dimension) {
        registry.counter(Obs.CACHE_MISS, Obs.TAG_LAYER, layer, Obs.TAG_DIMENSION, dimension).increment();
    }

    /** 동일 키 동시 중복 질의 — single-flight 트리거 신호. */
    public void inflightDuplicate(String dimension) {
        registry.counter(Obs.INFLIGHT_DUP, Obs.TAG_DIMENSION, dimension).increment();
    }
}
