package com.lia.core.store;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/**
 * {@link ChunkStore}의 Spring AI `PgVectorStore` 구현 — 벡터 chunks 적재·검색(D54·D55).
 *
 * <p><b>임베딩은 VectorStore가 내부 수행</b>: {@code add}는 {@code content}를, {@code similaritySearch}는
 * 질의를 설정된 `EmbeddingModel`로 임베딩한다 — 적재·검색 동일 모델이 여기서 보장된다.
 *
 * <p>멱등: PgVectorStore의 PK는 UUID라 {@code source_id}를 PK로 쓸 수 없다. 대신 {@code source_id}를
 * metadata에 두고 <b>삭제-후-삽입</b>(같은 source_id를 필터 delete 후 add)으로 재색인을 멱등하게 만든다.
 */
public class PgVectorChunkStore implements ChunkStore {

    private final VectorStore vectorStore;

    public PgVectorChunkStore(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void replaceVersion(String lawId, String efYd, List<Chunk> chunks) {
        // 정본 단위 완전 교체: (lawId, efYd)의 기존 청크를 전부 삭제 → 현재 세트 삽입.
        // source_id 단위가 아니라 정본 단위라 stale(사라진 하위 청크·삭제 조문)이 남지 않는다.
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression version = b.and(b.eq("lawId", lawId), b.eq("efYd", efYd)).build();
        vectorStore.delete(version);
        if (!chunks.isEmpty()) {
            vectorStore.add(chunks.stream().map(PgVectorChunkStore::toDocument).toList());
        }
    }

    @Override
    public List<Chunk> search(String query, int topK) {
        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).build());
        return hits == null ? List.of() : hits.stream().map(PgVectorChunkStore::toChunk).toList();
    }

    private static Document toDocument(Chunk c) {
        Map<String, Object> md = new LinkedHashMap<>(c.metadata());
        md.put("source_id", c.sourceId());          // 역추적 키(PK는 UUID라 metadata에 둔다)
        return Document.builder().text(c.content()).metadata(md).build();
    }

    private static Chunk toChunk(Document d) {
        Object sid = d.getMetadata().getOrDefault("source_id", d.getId());
        return new Chunk(String.valueOf(sid), d.getText(), d.getMetadata());
    }
}
