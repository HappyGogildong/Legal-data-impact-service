package com.lia.core.pipeline.resolve;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.lia.core.pipeline.connector.RawLaw;
import com.lia.core.store.Chunk;
import com.lia.core.store.ChunkStore;

/**
 * {@link com.lia.core.pipeline.resolve.SourceAnalyzer}의 {@code semanticSearch} 훅 어댑터 —
 * pending ns 벡터 검색({@link ChunkStore})의 청크 히트를 <b>lawId로 dedupe</b>해 {@code RawLaw} 후보로 되살린다.
 *
 * <p>한 법령이 여러 청크(변경조문·요약)로 히트하므로, 랭킹 순서를 보존하며 lawId당 하나로 합쳐 후보 목록을 만든다.
 * 후보 메타(title·efYd)는 chunk metadata에서 온다([[RAGIndexer]]가 심어둠). 본문 없는 경량 후보다.
 */
public class ChunkStoreLawSearch implements Function<String, List<RawLaw>> {

    /** 후보 dedupe 전에 넉넉히 당길 청크 수 — 한 법령이 여러 청크를 차지하므로. */
    private static final int CHUNK_FETCH = 20;

    private final ChunkStore chunkStore;
    private final int candidateLimit;

    public ChunkStoreLawSearch(ChunkStore chunkStore, int candidateLimit) {
        this.chunkStore = chunkStore;
        this.candidateLimit = candidateLimit;
    }

    @Override
    public List<RawLaw> apply(String query) {
        List<Chunk> hits = chunkStore.search(query, CHUNK_FETCH);
        Map<String, RawLaw> byLaw = new LinkedHashMap<>();       // 순서 보존 dedupe
        for (Chunk c : hits) {
            String lawId = str(c, "lawId");
            if (lawId == null || byLaw.containsKey(lawId)) continue;
            byLaw.put(lawId, toRawLaw(c, lawId));
            if (byLaw.size() >= candidateLimit) break;
        }
        return new ArrayList<>(byLaw.values());
    }

    private static RawLaw toRawLaw(Chunk c, String lawId) {
        String efYd = str(c, "efYd");
        LocalDate effective = efYd == null ? null : LocalDate.parse(efYd);
        return new RawLaw(lawId, null, str(c, "title"), "시행예정", effective, null, null, null);
    }

    private static String str(Chunk c, String key) {
        Object v = c.metadata().get(key);
        return v == null ? null : v.toString();
    }
}
