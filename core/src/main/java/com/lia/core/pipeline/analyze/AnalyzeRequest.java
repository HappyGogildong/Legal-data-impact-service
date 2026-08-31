package com.lia.core.pipeline.analyze;

import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.plan.QueryType;

/**
 * 분석 1건 요청 — 해소된 시행예정 정본 + (있으면) 기준선. 검색이 아니라 <b>정확 조회된 정본</b>이 들어온다.
 *
 * @param dimension 분석 차원(이번 증분 SUMMARY·DIFF) · @param law 시행예정 정본 · @param baseline 시행중본(제정이면 null)
 */
public record AnalyzeRequest(QueryType dimension, Law law, Law baseline) {}
