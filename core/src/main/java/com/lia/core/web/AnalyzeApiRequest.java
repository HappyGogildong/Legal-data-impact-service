package com.lia.core.web;

import java.time.LocalDate;

import com.lia.core.pipeline.plan.LawRef;

/**
 * {@code POST /api/v1/analyses} 요청 바디([[service-api-spec]] §3.0).
 *
 * @param query 자연어 질문(필수) · @param lawRef 검색에서 이미 특정했으면(선택) · @param scope 특정 차원 힌트(선택, 이번 증분 미사용)
 */
public record AnalyzeApiRequest(String query, LawRefDto lawRef, java.util.List<String> scope) {

    /** 요청의 법령 참조 — 정본 단위 {@code (lawId, effectiveDate)} + (선택) 조문. */
    public record LawRefDto(String lawId, LocalDate effectiveDate, String articleNo) {
        public LawRef toDomain() { return new LawRef(lawId, effectiveDate, articleNo); }
    }

    /** 명시적 참조가 있으면 도메인 타입으로, 없으면 null(자연어에서 해소). */
    public LawRef explicitRef() { return lawRef == null ? null : lawRef.toDomain(); }
}
