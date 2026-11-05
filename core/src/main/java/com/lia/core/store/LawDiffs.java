package com.lia.core.store;

import java.util.Optional;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.law.Law;

/** 미리 만든 변경점 설명 읽기 포트(D80) — 분석 DIFF·배치가 쓴다. 구현은 {@code GroundedSummaries}. */
public interface LawDiffs {

    /**
     * 그 버전의 변경점 설명 — 정본·기준선 revision·프롬프트 버전이 지금과 같고 인용이 지금 근거 안일 때만.
     *
     * @param baseline 대조한 시행중본. 제정이면 null
     */
    Optional<ImpactResult> findCurrent(Law law, Law baseline);
}
