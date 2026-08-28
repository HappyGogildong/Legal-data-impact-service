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

    /** {@code source_id} 기준 upsert(멱등). 저장 시 {@code content}가 자동 임베딩된다. */
    void upsert(List<Chunk> chunks);

    /** 질의 유사도 top-k. 질의 임베딩은 구현체 안에서 수행. 이번 증분은 라운드트립 검증용. */
    List<Chunk> search(String query, int topK);
}
