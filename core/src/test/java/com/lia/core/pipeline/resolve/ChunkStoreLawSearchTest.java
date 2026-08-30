package com.lia.core.pipeline.resolve;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.lia.core.pipeline.connector.RawLaw;
import com.lia.core.store.Chunk;
import com.lia.core.store.ChunkStore;

/**
 * ChunkStoreLawSearch 단위 — pending ns 벡터 히트(청크)를 <b>lawId로 dedupe</b>해 {@code RawLaw} 후보로.
 * SourceAnalyzer.semanticSearch 훅에 꽂히는 어댑터. Fake ChunkStore, 임베딩·DB 무관.
 */
class ChunkStoreLawSearchTest {

    static class FakeChunkStore implements ChunkStore {
        final List<Chunk> hits;
        FakeChunkStore(List<Chunk> hits) { this.hits = hits; }
        @Override public void replaceVersion(String lawId, String efYd, List<Chunk> chunks) {}
        @Override public List<Chunk> search(String query, int topK) { return hits.stream().limit(topK).toList(); }
    }

    private static Chunk chunk(String lawId, String title, String efYd) {
        return new Chunk("LAW:" + lawId + "@" + efYd, "본문",
                Map.of("kind", "summary", "lawId", lawId, "title", title, "efYd", efYd, "namespace", "pending"));
    }

    @Test
    void 청크히트를_lawId로_dedupe해_RawLaw_후보로() {
        FakeChunkStore store = new FakeChunkStore(List.of(
                chunk("001809", "주택법", "2026-08-04"),
                chunk("001809", "주택법", "2026-08-04"),   // 같은 법 — 합쳐져야
                chunk("002000", "근로기준법", "2026-09-01")));
        ChunkStoreLawSearch search = new ChunkStoreLawSearch(store, 5);

        List<RawLaw> out = search.apply("전세 세입자한테 불리한 법");

        assertEquals(2, out.size(), "같은 lawId는 하나로 dedupe");
        assertEquals("001809", out.get(0).lawId(), "히트 순서(랭킹) 보존");
        assertEquals("주택법", out.get(0).title());
        assertEquals(LocalDate.of(2026, 8, 4), out.get(0).effectiveDate(), "efYd 파싱");
        assertEquals("시행예정", out.get(0).status());
        assertEquals("근로기준법", out.get(1).title());
    }
}
