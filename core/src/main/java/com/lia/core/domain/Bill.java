package com.lia.core.domain;

import java.time.LocalDate;
import java.util.List;

/** 정규화된 표준 법안 (파이프라인 Bill 과 동일 형태). */
public record Bill(
        String id,
        String billNo,
        String title,
        String summary,
        List<String> proposers,
        LocalDate proposeDate,
        String committee,
        Stage stage,
        LocalDate effectiveDate,
        String sourceType,
        String sourceUrl,
        String baselineLawId,
        List<Article> articles
) {
    public enum Stage { 발의, 위원회심사, 본회의, 정부이송, 공포, 시행 }
}
