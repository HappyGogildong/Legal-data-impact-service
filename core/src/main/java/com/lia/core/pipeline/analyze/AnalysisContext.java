package com.lia.core.pipeline.analyze;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.lia.core.pipeline.plan.QueryType;

/**
 * 분석 프롬프트에 주입할 <b>근거 블록 모음</b> — {@link ContextBuilder}가 정본에서 조립한다.
 * 각 블록의 {@code sourceId}가 인용 게이트({@code FaithfulnessGate})의 기준(그라운딩 무결성).
 */
public record AnalysisContext(QueryType dimension, String lawRef, List<SourceBlock> blocks) {

    /** 근거 한 조각 — 인용키·종류(article|amend|addenda|baseline)·본문. */
    public record SourceBlock(String sourceId, String kind, String text) {}

    /** 이 context가 주입한 모든 {@code source_id} — 게이트가 "인용 ⊆ 이 집합"을 검증. */
    public Set<String> injectedSourceIds() {
        return blocks.stream().map(SourceBlock::sourceId).collect(Collectors.toSet());
    }
}
