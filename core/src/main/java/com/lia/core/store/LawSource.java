package com.lia.core.store;

import java.time.LocalDate;
import java.util.Optional;

import com.lia.core.domain.law.Law;

/**
 * 정본 <b>정확 조회</b> 포트 — 해소된 {@code (lawId, effectiveDate)}로 시행예정 정본을,
 * {@code lawId}로 시행중 기준선을 읽는다. 벡터 검색이 아니다(그건 Discovery 전용, D56).
 *
 * <p>테스트 시임 + 소비자(QueryDispatcher)와 {@link LawStore} 구현의 결합 분리를 위한 좁은 포트다.
 * {@code resolve.LawLookup}(커넥터 기반 해소)과는 다른 관심사 — 이쪽은 저장된 정본 읽기.
 */
public interface LawSource {

    /** 시행예정 정본. 미적재면 empty. */
    Optional<Law> find(String lawId, LocalDate effectiveDate);

    /** 시행중 기준선(diff 기준). 제정이라 없으면 empty. */
    Optional<Law> findBaseline(String lawId);
}
