package com.lia.core.pipeline.analyze;

import java.util.Optional;
import java.util.Set;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.store.LawSummaries;
import com.lia.core.store.LawSummaryStore;

/**
 * 미리 만든 쉬운 요약 읽기(D67) — 저장본이 <b>지금의 근거 집합 안에서만</b> 인용할 때만 돌려준다.
 * 저장소의 유효성(정본 revision·프롬프트 버전)은 정규화·{@link ContextBuilder}·{@code Law.sourceId} 형식이 바뀌어
 * 주입되는 source_id 집합이 달라지는 경우를 잡지 못한다. 저장본을 지금 {@code ContextBuilder}가 SUMMARY 로 조립하는
 * 집합에 대고 엔진의 인용 검증({@code AnalysisEngine.grounded} — 주장 ≥1·주장마다 인용 ≥1·claims·impacts·actions 의
 * 인용이 모두 집합 안)을 그대로 다시 돌려, 통과하지 못하면 낡은 것(empty)으로 본다.
 * 분석(SummaryHandler)·법령 상세(LawController)·배치(SummarizeHandler)가 이 포트 하나를 공유한다.
 */
public class GroundedSummaries implements LawSummaries {

    private final LawSummaryStore store;
    private final ContextBuilder contextBuilder;

    public GroundedSummaries(LawSummaryStore store, ContextBuilder contextBuilder) {
        this.store = store;
        this.contextBuilder = contextBuilder;
    }

    @Override
    public Optional<ImpactResult> findCurrent(Law law) {
        return store.findStored(law).filter(stored -> isGrounded(stored, law));
    }

    private boolean isGrounded(ImpactResult stored, Law law) {
        Set<String> injected = contextBuilder.build(new AnalyzeRequest(QueryType.SUMMARY, law, null)).injectedSourceIds();
        return AnalysisEngine.grounded(stored, injected);
    }
}
