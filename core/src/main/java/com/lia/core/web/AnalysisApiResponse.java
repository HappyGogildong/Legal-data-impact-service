package com.lia.core.web;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.pipeline.discover.DiscoveredLaw;
import com.lia.core.pipeline.dispatch.Unmet;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code POST /api/v1/analyses} 응답([[service-api-spec]] §3.0). 해소 성공(RESOLVED)과 미해소(4상태)를
 * 한 레코드로 표현하고, <b>값이 없는 최상위 필드는 생략</b>한다. 키는 camelCase.
 *
 * @param resolution   RESOLVED · NOT_FOUND_YET · AMBIGUOUS · UNVERIFIED
 * @param message      미해소 안내(RESOLVED 면 없음)
 * @param candidates   AMBIGUOUS 후보
 * @param law          RESOLVED 대상 법령 헤더
 * @param alternatives 같은 법령의 다른 시행예정본(D43)
 * @param answer       채운 차원 → 결과(키: 차원 소문자, QueryType 선언 순)
 * @param unmet        못 채운 차원 → 사유 코드·설명
 * @param sources      answer 안 인용키의 근거(인용 칩·근거 패널)
 * @param disclaimer   면책(응답 전역)
 * @param discovery    법령 발견 결과(Discovery 질문만, D70)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnalysisApiResponse(
        @Schema(allowableValues = {"RESOLVED", "NOT_FOUND_YET", "AMBIGUOUS", "UNVERIFIED"}) String resolution,
        String message,
        List<LawBrief> candidates,
        LawHeader law,
        List<LawBrief> alternatives,
        Map<String, ImpactResult> answer,
        Map<String, Unmet> unmet,
        List<CitedSource> sources,
        String disclaimer,
        Discovery discovery) {

    public static AnalysisApiResponse analyzed(LawHeader law, List<LawBrief> alternatives,
                                               Map<String, ImpactResult> answer, Map<String, Unmet> unmet,
                                               List<CitedSource> sources, String disclaimer) {
        return new AnalysisApiResponse("RESOLVED", null, null, law, alternatives, answer, unmet, sources, disclaimer, null);
    }

    public static AnalysisApiResponse unresolved(String resolution, String message, List<LawBrief> candidates) {
        return new AnalysisApiResponse(resolution, message, candidates.isEmpty() ? null : candidates,
                null, null, null, null, null, null, null);
    }

    public static AnalysisApiResponse discovered(Discovery discovery) {
        return new AnalysisApiResponse("RESOLVED", null, null, null, null, null, null, null, null, discovery);
    }

    /** 법령 발견 결과 — 후보 카드, 프로필 라벨을 붙였는지, 토글을 켰지만 프로필을 못 쓴 사유(없으면 키 생략). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Discovery(List<DiscoveredLaw> candidates, boolean profileApplied, Unmet profileBlocked) {}

    /** 대상 법령 — 정본 미적재면 title·lawType·ministry 는 null. {@code ref} = "LAW:{lawId}@{efYd}". */
    public record LawHeader(String ref, String lawId, String title, LocalDate effectiveDate,
                            String lawType, String ministry) {}

    public record LawBrief(String lawId, String title, LocalDate effectiveDate) {}

    /**
     * 근거 — 찾지 못하면 label=sourceId, text=null. changeType 은 조문만(신설·개정·삭제·이동·없음).
     * previousText 는 시행예정본의 개정·삭제·이동 조문일 때 현행 원문(조문 대비용), 그 밖엔 null.
     */
    public record CitedSource(String sourceId, String label, String text, String previousText,
                              String changeType, LocalDate effectiveDate) {}
}
