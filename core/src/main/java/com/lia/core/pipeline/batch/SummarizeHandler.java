package com.lia.core.pipeline.batch;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.analyze.AnalysisEngine;
import com.lia.core.pipeline.analyze.AnalyzeRequest;
import com.lia.core.pipeline.analyze.AnalyzeResponse;
import com.lia.core.pipeline.analyze.InsufficientGroundingException;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.store.LawDiffs;
import com.lia.core.store.LawStore;
import com.lia.core.store.LawSummaries;
import com.lia.core.store.LawSummaryStore;

/**
 * SUMMARIZE — 저장된 시행예정 정본으로 Layer A(쉬운 요약 D67·변경점 D80)의 빠진 차원을 만들어 저장. 차원마다 Opus 1회,
 * 한쪽의 검증 실패가 다른 쪽을 막지 않고 일시 오류로 재시도하면 이미 저장한 차원은 건너뛴다.
 * 분석과 같은 엔진(조립→추론→인용 검증→재생성).
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
    private final LawDiffs currentDiffs;
    private final MinIntervalLimiter limiter;
    private final String model;

    public SummarizeHandler(LawStore lawStore, AnalysisEngine engine, LawSummaryStore store,
                            LawSummaries current, LawDiffs currentDiffs, MinIntervalLimiter limiter, String model) {
        this.lawStore = lawStore;
        this.engine = engine;
        this.store = store;
        this.current = current;
        this.currentDiffs = currentDiffs;
        this.limiter = limiter;
        this.model = model;
    }

    @Override
    public TaskKind kind() {
        return TaskKind.SUMMARIZE;
    }

    @Override
    public void handle(Task task) {
        Optional<Law> found = lawStore.find(task.lawId(), task.effectiveDate());
        if (found.isEmpty() || !found.get().pending()) {
            return;
        }
        Law law = found.get();
        Law baseline = lawStore.findBaseline(law.lawId()).orElse(null);
        // 이미 유효한(지금 근거 안에서 인용하는) 차원은 유료 호출 없이 건너뛴다
        Set<QueryType> missing = LayerATargets.missing(law, baseline, current, currentDiffs);
        if (missing.contains(QueryType.SUMMARY)) {
            generate(new AnalyzeRequest(QueryType.SUMMARY, law, null), result -> store.save(law, result, model));
        }
        if (missing.contains(QueryType.DIFF)) {
            generate(new AnalyzeRequest(QueryType.DIFF, law, baseline), result -> store.saveDiff(law, baseline, result, model));
        }
    }

    private void generate(AnalyzeRequest request, Consumer<ImpactResult> save) {
        limiter.acquire();
        try {
            AnalyzeResponse response = engine.analyze(request);
            save.accept(response.result());
        } catch (InsufficientGroundingException e) {
            log.warn("[batch] {} 인용 검증 실패 — 저장 안 함: {}@{}", request.dimension(), request.law().lawId(),
                    request.law().effectiveDate());
        }
    }
}
