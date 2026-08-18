package com.lia.core.eval;

import java.util.List;

/**
 * 검색 포트 — 평가 하네스가 리트리버를 <b>인터페이스 뒤로 격리</b>한다.
 *
 * <p>지금은 테스트의 {@code FakeRetriever}(합성 랭킹)로 스캐폴딩을 검증하고,
 * RAG(Embedder/Vector Store)가 landing하면 실제 벡터 검색 구현으로 교체한다
 * ({@code SourceAnalyzer}의 {@code semanticSearch} 포트와 동일 패턴).
 */
@FunctionalInterface
public interface Retriever {

    /** 질의에 대해 랭킹된 검색 결과(rank 1 우선). */
    List<Retrieved> retrieve(String query, RagConfig config);

    /** 검색 결과 1건 — 인용키(source_id)와 유사도 점수. */
    record Retrieved(String sourceId, double score) {}
}
