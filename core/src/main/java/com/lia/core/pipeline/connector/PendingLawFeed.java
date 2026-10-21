package com.lia.core.pipeline.connector;

import java.time.LocalDate;
import java.util.List;

/**
 * 배치가 쓰는 법령 원천 포트(D62) — 시행예정 목록·본문·기준선. 구현은 {@link LawConnector},
 * 테스트는 가짜 구현으로 갈아 끼운다.
 */
public interface PendingLawFeed {

    /** 시행일이 {@code from~to}인 시행예정 목록 <b>전량</b> + 끝까지 받았는지. 실패하면 예외. */
    PendingList listAllPending(LocalDate from, LocalDate to);

    /** 시행예정 본문(MST 로 특정). */
    RawLaw fetchPending(String mst, LocalDate effectiveDate);

    /** 시행중 본문(diff 기준선). 없으면 null(제정). */
    RawLaw fetchCurrent(String lawId);

    /**
     * @param heads    목록 행(본문 없음 — lawId·mst·시행일)
     * @param complete 총건수(totalCnt)만큼 다 받았는가. false 면 사라짐 판정을 하면 안 된다
     */
    record PendingList(List<RawLaw> heads, boolean complete) {

        public PendingList {
            heads = List.copyOf(heads);
        }

        /** {@code totalCount < 0}은 API 가 총건수를 주지 않은 것 — 완주를 확인할 수 없으므로 미완주. */
        public static PendingList of(List<RawLaw> heads, int totalCount) {
            return new PendingList(heads, totalCount >= 0 && heads.size() >= totalCount);
        }
    }
}
