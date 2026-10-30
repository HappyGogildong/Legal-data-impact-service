package com.lia.core.web;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.lia.core.store.AnalysisHistoryStore.HistoryRow;

/** {@code /api/v1/history} 요청·응답(D73). 스냅샷 본문은 {@link HistorySnapshot}. */
final class HistoryApiDtos {

    private HistoryApiDtos() {}

    public record HistorySaveRequest(String analysisId) {}

    public record HistorySavedResponse(long id) {}

    public record HistoryItem(long id, String query, String lawId, String lawTitle, LocalDate effectiveDate,
                              OffsetDateTime savedAt, boolean lawUpdated) {

        static HistoryItem from(HistoryRow row) {
            return new HistoryItem(row.id(), row.query(), row.lawId(), row.lawTitle(), row.effectiveDate(),
                    row.savedAt(), row.lawUpdated());
        }
    }

    public record HistoryListResponse(List<HistoryItem> items) {}

    /** 저장 당시 결과 — 서버가 세션에 보관한 것으로 만든다(D73 ③). */
    public record HistorySnapshot(AnalysisApiResponse body, ActionRevisionApiResponse revision,
                                  List<HttpSessionRecentAnalyses.AnsweredQuestion> answers) {}

    /** @param revision 대응 방안 재생성본(없으면 생략) @param latestEffectiveDate 다시 분석할 버전(없으면 생략) */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HistoryDetailResponse(long id, String query, String lawId, String lawTitle, LocalDate effectiveDate,
                                        OffsetDateTime savedAt, boolean lawUpdated, AnalysisApiResponse body,
                                        ActionRevisionApiResponse revision,
                                        List<HttpSessionRecentAnalyses.AnsweredQuestion> answers,
                                        LocalDate latestEffectiveDate) {}
}
