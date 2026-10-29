package com.lia.core.pipeline.discover;

import java.util.List;

/**
 * 법령 발견 결과(D70) — 질문과 관련 있어 보이는 시행예정 법령 후보(유사도 순) + 프로필 라벨을 검색어에 붙였는지.
 * 후보가 없으면 빈 목록(오류 아님).
 */
public record DiscoveryResult(List<DiscoveredLaw> candidates, boolean profileApplied) {
    public DiscoveryResult {
        candidates = List.copyOf(candidates);
    }
}
