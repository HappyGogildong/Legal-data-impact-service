package com.lia.core.observability;

/**
 * 관측 지표·태그 이름을 <b>한곳에 고정</b>한다(D48).
 *
 * <p>이름이 코드 곳곳에 흩어지면 대시보드·알림과 어긋난다. 계측 지점은 이 상수만 쓴다.
 * 지표 명세: docs/backend/observability.md §1 대상 카탈로그.
 *
 * <ul>
 *   <li><b>live-now</b> — 오프라인 파이프라인 단계. {@code Observation} 으로 감싸 <i>타이머 + span</i> 동시 생성.</li>
 *   <li><b>hook</b> — 온라인 경로(dispatch·AnalysisEngine) landing 시 {@link LiaMetrics} 가 발화.</li>
 * </ul>
 */
public final class Obs {

    private Obs() {}

    // --- live-now: 오프라인 파이프라인 단계 (Observation = timer + span) ---
    /** LawConnector 본문 fetch — 네트워크+API+파싱. 태그 {@link #TAG_TARGET}. */
    public static final String CONNECTOR_FETCH = "lia.connector.fetch";
    /** Normalizer.normalize — 순수 CPU(조문 병합·부칙 필터·revision). */
    public static final String NORMALIZE = "lia.normalize";
    /** DiffBuilder.build — 순수 CPU(변경조문↔시행중본 대조). */
    public static final String DIFF = "lia.diff";
    /** SourceAnalyzer.resolve — 법령명 매칭(+의미검색). 태그 {@link #TAG_STATE}. */
    public static final String RESOLVE = "lia.resolve";
    /** 적재 1건 end-to-end(데모 러너/배치). */
    public static final String INGEST = "lia.ingest";
    // 임베딩 계측은 Spring AI 내장(gen_ai.client.operation, GenAI 컨벤션)에 위임 — lia.embed 두지 않음.

    // --- hook: 온라인 경로 landing 시 발화 (LiaMetrics) ---
    public static final String LLM_CALLS = "lia.analysis.llm.calls";
    public static final String CACHE_HIT = "lia.analysis.cache.hit";
    public static final String CACHE_MISS = "lia.analysis.cache.miss";
    public static final String INFLIGHT_DUP = "lia.analysis.inflight.duplicate";

    // --- 태그 키 (저카디널리티만) ---
    public static final String TAG_TARGET = "target";       // eflaw | law
    public static final String TAG_STATE = "state";         // 해소 4상태
    public static final String TAG_MODEL = "model";         // opus | haiku
    public static final String TAG_DIMENSION = "dimension"; // LOOKUP|SUMMARY|DIFF|IMPACT|ACTION
    public static final String TAG_LAYER = "layer";         // A | B
}
