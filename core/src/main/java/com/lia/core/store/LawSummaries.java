package com.lia.core.store;

import java.util.Optional;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.law.Law;

/** 미리 만든 쉬운 요약 읽기 포트(D67) — 분석 SUMMARY·법령 상세가 쓴다. 구현은 {@code GroundedSummaries}(저장소 {@link LawSummaryStore} 위에서 인용의 근거까지 확인). */
public interface LawSummaries {

    /** 그 버전의 요약 — 정본 revision·프롬프트 버전이 지금과 같고 인용이 지금 근거 안일 때만. 없거나 낡으면 empty. */
    Optional<ImpactResult> findCurrent(Law law);
}
