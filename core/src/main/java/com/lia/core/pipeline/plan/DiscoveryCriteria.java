package com.lia.core.pipeline.plan;

import java.util.List;

/**
 * 코퍼스 검색(Discovery) 조건 — 특정 법령이 아니라 "나에게 영향 있을 법령 찾아줘"(S4~7).
 * {@code profileBound}이면 프로필 기반 검색(없으면 키워드/도메인으로 강등).
 */
public record DiscoveryCriteria(List<String> keywords, List<String> conditions,
                                List<String> domains, boolean profileBound) {
    public DiscoveryCriteria {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        domains = domains == null ? List.of() : List.copyOf(domains);
    }
}
