package com.lia.core.pipeline.analyze;

import java.util.Set;

import com.lia.core.domain.analysis.ImpactResult;

/**
 * 분석 응답 — 검증 통과한 {@link ImpactResult} + <b>주입 source_id 집합</b>(2차 검증 게이트 #12 입력).
 */
public record AnalyzeResponse(ImpactResult result, Set<String> injectedSourceIds) {}
