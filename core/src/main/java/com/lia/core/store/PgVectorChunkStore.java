package com.lia.core.store;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link ChunkStore}의 Spring AI `PgVectorStore` 구현 — 벡터 chunks 적재·검색(D54·D55).
 *
 * <p><b>임베딩은 VectorStore가 내부 수행</b>: {@code add}는 {@code content}를, {@code similaritySearch}는
 * 질의를 설정된 `EmbeddingModel`로 임베딩한다 — 적재·검색 동일 모델이 여기서 보장된다.
 *
 * <p>멱등: 재색인은 정본 버전 {@code (lawId, efYd)} 단위 <b>삭제+삽입을 한 트랜잭션</b>으로 한다
 * ({@link #replaceVersion}) — 그 버전의 기존 청크를 모두 지우고 현재 세트를 넣어 사라진 조문의 청크가 남지 않는다.
 * PgVectorStore의 PK는 UUID라 {@code source_id}는 metadata에 두는 역추적 키다.
 */
public class PgVectorChunkStore implements ChunkStore {

    private final VectorStore vectorStore;
    private final TransactionTemplate transactions;

    /**
     * @param transactions PgVectorStore 와 같은 DataSource 의 트랜잭션 — {@link #replaceVersion}의
     *                     삭제+삽입을 한 트랜잭션으로 묶는다(D62)
     */
    public PgVectorChunkStore(VectorStore vectorStore, TransactionTemplate transactions) {
        this.vectorStore = vectorStore;
        this.transactions = transactions;
    }

    @Override
    public void replaceVersion(String lawId, String efYd, List<Chunk> chunks) {
        // 정본 단위 완전 교체: (lawId, efYd)의 기존 청크를 전부 삭제 → 현재 세트 삽입.
        // source_id 단위가 아니라 정본 단위라 stale(사라진 하위 청크·삭제 조문)이 남지 않는다.
        // 한 트랜잭션(D62): 커밋 전까지 검색은 이전 청크를 보고, 임베딩 등 삽입이 실패하면 롤백돼 이전 청크가 남는다.
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression version = b.and(b.eq("lawId", lawId), b.eq("efYd", efYd)).build();
        transactions.executeWithoutResult(status -> {
            vectorStore.delete(version);
            if (!chunks.isEmpty()) {
                vectorStore.add(chunks.stream().map(PgVectorChunkStore::toDocument).toList());
            }
        });
    }

    @Override
    public List<Chunk> search(String query, int topK) {
        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).build());
        return hits.stream().map(PgVectorChunkStore::toChunk).toList();
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
