package com.lia.core.pipeline.dispatch;

import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.plan.AnalysisQuery;

/**
 * 핸들러 실행 재료 — {@code QueryDispatcher}가 정본을 1회 조회해 조립한다.
 * 모든 Layer A 핸들러가 같은 정본을 쓰므로 중복 fetch를 제거한다.
 *
 * @param law 해소된 시행예정 정본(필수) · @param baseline 시행중 기준선(제정이면 null, D42) ·
 *            @param query 원 질의(intent·scope·profileBound 참조용)
 */
public record DispatchContext(Law law, Law baseline, AnalysisQuery query) {}
