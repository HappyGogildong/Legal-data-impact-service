package com.lia.core.pipeline.batch;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.analyze.AnalysisEngine;
import com.lia.core.pipeline.analyze.AnalyzeRequest;
import com.lia.core.pipeline.analyze.AnalyzeResponse;
import com.lia.core.pipeline.analyze.InsufficientGroundingException;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.store.LawStore;
import com.lia.core.store.LawSummaries;
import com.lia.core.store.LawSummaryStore;

/**
 * SUMMARIZE — 저장된 시행예정 정본으로 쉬운 요약을 만들어 저장(D67). 분석과 같은 엔진(조립→추론→인용 검증→재생성).
 * 검증이 끝내 실패하면 저장하지 않고 끝낸다 — 같은 입력이라 재시도해도 같은 결과(화면은 실시간 대체).
 * {@code limiter}는 <b>작업(task) 사이</b>의 간격을 제한한다 — 인용 검증 실패로 엔진이 재생성하는 시도마다 간격을 두지 않는다
 * (재생성은 엔진의 {@code maxAttempts}가 상한). 시행예정이 아니게 됐거나 유효한 요약이 이미 있으면 유료 호출 없이 끝난다 —
 * 유효 = 정본 revision·프롬프트 버전 일치 + 인용이 지금 근거 집합 안({@code current}는 그라운딩까지 확인하는 읽기, 쓰기는 {@code store})
 * (backfill 재등록·저장 후 DONE 전 재시도에도 중복 호출 0).
 */
public class SummarizeHandler implements TaskHandler {

    private static final Logger log = LoggerFactory.getLogger(SummarizeHandler.class);

    private final LawStore lawStore;
    private final AnalysisEngine engine;
    private final LawSummaryStore store;
    private final LawSummaries current;
    private final MinIntervalLimiter limiter;
    private final String model;

    public SummarizeHandler(LawStore lawStore, AnalysisEngine engine, LawSummaryStore store,
                            LawSummaries current, MinIntervalLimiter limiter, String model) {
        this.lawStore = lawStore;
        this.engine = engine;
        this.store = store;
        this.current = current;
        this.limiter = limiter;
        this.model = model;
    }

    @Override
    public TaskKind kind() {
        return TaskKind.SUMMARIZE;
    }

    @Override
    public void handle(Task task) {
        Optional<Law> law = lawStore.find(task.lawId(), task.effectiveDate());
        if (law.isEmpty() || !law.get().pending()) {
            return;
        }
        if (current.findCurrent(law.get()).isPresent()) {
            return;   // 이미 유효한(지금 근거 안에서 인용하는) 요약 — 유료 호출 없음
        }
        limiter.acquire();
        try {
            AnalyzeResponse response = engine.analyze(new AnalyzeRequest(QueryType.SUMMARY, law.get(), null));
            store.save(law.get(), response.result(), model);
        } catch (InsufficientGroundingException e) {
            log.warn("[batch] 쉬운 요약 인용 검증 실패 — 저장 안 함: {}@{}", task.lawId(), task.effectiveDate());
        }
    }
}
