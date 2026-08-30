package com.lia.core.eval;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.lia.core.eval.Retriever.Retrieved;
import com.lia.core.store.Chunk;
import com.lia.core.store.ChunkStore;

/**
 * ChunkStoreRetriever 단위 — Fake ChunkStore로 <b>매핑·순서·topK 전달</b>만 검증(임베딩·DB 무관).
 * 실 검색 품질은 게이트 스모크/평가에서.
 */
class ChunkStoreRetrieverTest {

    /** 고정 순서 청크를 돌려주고 topK를 붙잡는 Fake. */
    static class FakeChunkStore implements ChunkStore {
        int seenTopK = -1;
        final List<Chunk> hits;
        FakeChunkStore(List<Chunk> hits) { this.hits = hits; }
        @Override public void upsert(List<Chunk> chunks) {}
        @Override public List<Chunk> search(String query, int topK) {
            this.seenTopK = topK;
            return hits.stream().limit(topK).toList();
        }
    }

    private static Chunk chunk(String sourceId) {
        return new Chunk(sourceId, "본문", Map.of("kind", "article"));
    }

    @Test
    void search결과를_순서대로_Retrieved로_매핑하고_topK를_전달한다() {
        FakeChunkStore store = new FakeChunkStore(List.of(
                chunk("LAW:001809@2026-08-04:art:18"),
                chunk("LAW:001809@2026-08-04:art:104"),
                chunk("LAW:001809@2026-08-04")));
        Retriever retriever = new ChunkStoreRetriever(store);

        List<Retrieved> out = retriever.retrieve("통합심의", RagConfig.defaults().withTopK(3));

        assertEquals(3, store.seenTopK, "config.topK가 search에 전달돼야");
        assertEquals(List.of("LAW:001809@2026-08-04:art:18", "LAW:001809@2026-08-04:art:104",
                        "LAW:001809@2026-08-04"),
                out.stream().map(Retrieved::sourceId).toList(), "히트 순서 보존");
        assertTrue(out.get(0).score() > out.get(1).score(), "앞 순위가 높은 점수");
    }
}
