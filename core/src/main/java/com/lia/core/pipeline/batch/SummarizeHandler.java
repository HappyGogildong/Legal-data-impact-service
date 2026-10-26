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
import com.lia.core.store.LawSummaryStore;

/**
 * SUMMARIZE — 저장된 시행예정 정본으로 쉬운 요약을 만들어 저장(D67). 분석과 같은 엔진(조립→추론→인용 검증→재생성).
 * 검증이 끝내 실패하면 저장하지 않고 끝낸다 — 같은 입력이라 재시도해도 같은 결과(화면은 실시간 대체).
 * 유료(Opus) 호출 간격은 {@code limiter}로 제한한다. 시행예정이 아니게 됐으면 할 일이 없다.
 */
public class SummarizeHandler implements TaskHandler {

    private static final Logger log = LoggerFactory.getLogger(SummarizeHandler.class);

    private final LawStore lawStore;
    private final AnalysisEngine engine;
    private final LawSummaryStore summaries;
    private final MinIntervalLimiter limiter;
    private final String model;

    public SummarizeHandler(LawStore lawStore, AnalysisEngine engine, LawSummaryStore summaries,
                            MinIntervalLimiter limiter, String model) {
        this.lawStore = lawStore;
        this.engine = engine;
        this.summaries = summaries;
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
        limiter.acquire();
        try {
            AnalyzeResponse response = engine.analyze(new AnalyzeRequest(QueryType.SUMMARY, law.get(), null));
            summaries.save(law.get(), response.result(), model);
        } catch (InsufficientGroundingException e) {
            log.warn("[batch] 쉬운 요약 인용 검증 실패 — 저장 안 함: {}@{}", task.lawId(), task.effectiveDate());
        }
    }
}
