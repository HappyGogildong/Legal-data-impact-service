package com.lia.core.pipeline.plan;

import java.time.LocalDate;

import com.lia.core.domain.law.Article;

/**
 * 해소된 법령 참조 — 정본 단위 {@code (lawId, effectiveDate)}(D43) + (선택) 조문번호.
 * {@code articleNo}가 있으면 특정 조문 질의(S2). 번역기·API 가 쓴 표기를 생성자가 {@code Article.no}
 * 형식으로 맞추고, 한 조문으로 해석되지 않으면 null 이다(법 전체, D82).
 */
public record LawRef(String lawId, LocalDate effectiveDate, String articleNo) {
    public LawRef {
        articleNo = Article.normalizeNo(articleNo);
    }
}
