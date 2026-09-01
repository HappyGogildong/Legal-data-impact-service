package com.lia.core.pipeline.analyze;

import java.util.Objects;

import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.plan.QueryType;

/**
 * 분석 1건 요청 — 해소된 시행예정 정본 + (있으면) 기준선. 검색이 아니라 <b>정확 조회된 정본</b>이 들어온다.
 *
 * <p>우리가 산출한 판정 타입이라 <b>생성자가 불변식을 강제</b>한다(dimension·law 필수). 덕분에
 * {@link ContextBuilder}는 방어 없이 assembly에만 집중한다. {@code baseline}은 제정이면 null(정상, D42).
 *
 * @param dimension 분석 차원(이번 증분 SUMMARY·DIFF) · @param law 시행예정 정본 · @param baseline 시행중본(제정이면 null)
 */
public record AnalyzeRequest(QueryType dimension, Law law, Law baseline) {

    public AnalyzeRequest {
        Objects.requireNonNull(dimension, "dimension은 필수다.");
        Objects.requireNonNull(law, "law는 필수다 — 해소된 정본이 들어와야 한다.");
    }
}
