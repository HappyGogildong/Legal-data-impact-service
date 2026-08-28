package com.lia.core.pipeline.plan;

import java.time.LocalDate;

/**
 * 해소된 법령 참조 — 정본 단위 {@code (lawId, effectiveDate)}(D43) + (선택) 조문번호.
 * {@code articleNo}가 있으면 특정 조문 질의(S2).
 */
public record LawRef(String lawId, LocalDate effectiveDate, String articleNo) {}
