package com.lia.core.eval;

import java.util.ArrayList;
import java.util.List;

import com.lia.core.store.Chunk;
import com.lia.core.store.ChunkStore;

/**
 * 실물 {@link Retriever} — {@link ChunkStore}(pgvector, 내부 임베딩) 검색을 평가·런타임이 공유하도록 잇는다.
 *
 * <p>{@code ChunkStore.search}가 순위대로 청크를 돌려주므로, 점수는 <b>순위 기반</b>(1/(rank+1))으로 부여한다
 * — 유사도 원점수는 노출하지 않으며 지표(recall·mrr 등)는 순서만 쓴다. 실 검색 품질은 게이트 스모크·평가에서.
 */
public class ChunkStoreRetriever implements Retriever {

    private final ChunkStore chunkStore;

    public ChunkStoreRetriever(ChunkStore chunkStore) {
        this.chunkStore = chunkStore;
    }

    @Override
    public List<Retrieved> retrieve(String query, RagConfig config) {
        List<Chunk> hits = chunkStore.search(query, config.topK());
        List<Retrieved> out = new ArrayList<>(hits.size());
        for (int i = 0; i < hits.size(); i++) {
            out.add(new Retrieved(hits.get(i).sourceId(), 1.0 / (i + 1)));
        }
        return out;
    }
}
