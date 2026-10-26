package com.lia.core.store;

import java.time.LocalDate;

/**
 * 조회 1회 기록 포트(D66) — 분석 유스케이스가 저장소 구현을 모른 채 센다. 구현은 {@link LawViewStore}.
 */
public interface LawViews {

    /** 서울 오늘 기준 그 버전의 조회 +1. 저장된 버전이 없으면 기록하지 않고 false. */
    boolean record(String lawId, LocalDate effectiveDate);
}
