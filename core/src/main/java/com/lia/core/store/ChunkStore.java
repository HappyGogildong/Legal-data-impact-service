package com.lia.core.store;

import java.util.List;

/**
 * 벡터 chunks 적재·검색 포트 — Spring AI `PgVectorStore` 구현이 뒤에 온다.
 *
 * <p>[[LawStore]](JSONB 정본, JdbcClient)와 분리 — chunks는 다른 라이브러리·다른 테이블.
 * upsert/search 시 <b>임베딩은 구현체 안 PgVectorStore가 내부 수행</b>(공유 EmbeddingModel) —
 * 호출자는 벡터를 다루지 않는다. 스펙: docs/components/ChunkStore.md.
 */
public interface ChunkStore {

    /**
     * 한 정본 {@code (lawId, efYd)}의 벡터를 <b>현재 세트로 완전 교체</b>한다 — 그 정본의 기존 청크를
     * 모두 삭제한 뒤 {@code chunks}를 삽입. <b>재색인 시 저장 상태 == 현재 법령 상태</b>를 보장한다
     * (분할 개수가 줄거나 조문이 삭제/이동돼도 stale 청크가 남지 않음).
     *
     * <p>{@code source_id} 단위 upsert가 아니라 <b>정본 단위 replace</b>인 이유: 한 정본의 청크 집합은
     * 함께 바뀌므로(분할 수 변동 등) 개별 id 덮어쓰기로는 사라진 청크를 정리할 수 없다.
     * {@code chunks}가 비면 그 정본의 청크를 전부 지운다(삭제 반영). 저장 시 {@code content} 자동 임베딩.
     *
     * @param lawId 법령ID · @param efYd 시행일(ISO, chunk 메타 {@code efYd}와 일치)
     */
    void replaceVersion(String lawId, String efYd, List<Chunk> chunks);

    /** 질의 유사도 top-k. 질의 임베딩은 구현체 안에서 수행. 이번 증분은 라운드트립 검증용. */
    List<Chunk> search(String query, int topK);
}
