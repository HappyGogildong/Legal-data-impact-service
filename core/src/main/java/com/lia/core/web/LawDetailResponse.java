package com.lia.core.web;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code GET /api/v1/laws/{lawId}/{efYd}} — 법령 상세(S05, [[service-api-spec]] §3.3). 정본에서 결정론으로 만든 사실만
 * (요약 없음, D65). 값이 없는 필드는 생략한다.
 *
 * @param amendReason           제개정이유 원문
 * @param effectiveClause       부칙의 시행일 조항, 없으면 생략
 * @param changedArticles       조문변경여부='Y' 조문만
 * @param baselineEffectiveDate 현행 대비에 쓴 시행중본의 시행일 — 시행예정본이고 기준선이 있을 때만
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LawDetailResponse(
        String lawId,
        String title,
        String lawType,
        String ministry,
        LocalDate promulgateDate,
        String promulgateNo,
        LocalDate effectiveDate,
        @Schema(allowableValues = {"시행예정", "시행중", "과거", "철회"}) String status,
        String amendKind,
        String sourceUrl,
        String amendReason,
        @Schema(allowableValues = {"즉시", "유예", "단계적"}) String enforcementType,
        String effectiveRule,
        SourceText effectiveClause,
        List<ChangedArticle> changedArticles,
        List<SourceText> addenda,
        List<String> delegationClauses,
        LocalDate baselineEffectiveDate) {

    /**
     * 바뀐 조문 — label 은 "제18조(제목)". previousText 는 시행예정본의 개정·삭제·이동 조문일 때 현행 원문, 그 밖엔 생략.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChangedArticle(String sourceId, String no, String label,
                                 @Schema(allowableValues = {"신설", "개정", "삭제", "이동", "없음"}) String changeType,
                                 String text, String previousText) {}

    /** 인용키가 있는 원문 조각(부칙 등) — label 은 "부칙 제1조(시행일)". */
    public record SourceText(String sourceId, String label, String text) {}
}
