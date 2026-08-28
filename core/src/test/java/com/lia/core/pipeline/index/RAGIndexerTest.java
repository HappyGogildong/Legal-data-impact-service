package com.lia.core.pipeline.index;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Article.ChangeType;
import com.lia.core.domain.law.Law;
import com.lia.core.store.Chunk;
import com.lia.core.store.ChunkStore;

/**
 * RAGIndexer 단위 — Fake ChunkStore가 upsert된 Chunk를 캡처. <b>순수 청킹 로직</b>만 본다
 * (임베딩은 ChunkStore/PgVectorStore 몫이라 여기 없음).
 */
class RAGIndexerTest {

    /** 넣은 Chunk를 붙잡아 두는 테스트 더블. */
    static class CapturingChunkStore implements ChunkStore {
        final List<Chunk> captured = new ArrayList<>();
        @Override public void upsert(List<Chunk> chunks) { captured.addAll(chunks); }
    }

    private final CapturingChunkStore store = new CapturingChunkStore();
    private final RAGIndexer indexer = new RAGIndexer(store);

    @Test
    void 변경조문과_요약만_청킹하고_미변경조문은_제외한다() {
        Law law = law(
                "제18조 개정 본문 …",   // 변경
                "제104조 개정 본문 …",  // 변경
                "제2조 미변경 정의 …");  // 미변경 → 색인 제외

        indexer.index(law);

        // 조문 2 + 요약 1 = 3
        assertEquals(3, store.captured.size());

        Chunk art18 = chunk(store.captured, law.sourceId(law.article("18")));
        assertEquals("제18조 개정 본문 …", art18.content());
        assertEquals("article", art18.metadata().get("kind"));
        assertEquals("pending", art18.metadata().get("namespace"));
        assertEquals("001809", art18.metadata().get("lawId"));
        assertEquals("18", art18.metadata().get("articleNo"));

        // 요약 = amendReason, source_id = law.ref()
        Chunk summary = chunk(store.captured, law.ref());
        assertEquals("summary", summary.metadata().get("kind"));
        assertTrue(summary.content().contains("개정이유"));

        // 미변경 제2조는 없음
        assertTrue(store.captured.stream().noneMatch(c -> c.sourceId().endsWith(":art:2")));
    }

    @Test
    void 과대_조문은_오버랩_분할된다() {
        String big = "가".repeat(13000);   // > MAX_CHARS(6000)
        Law law = law(big, "제104조 짧음", "제2조 미변경");

        indexer.index(law);

        String base = law.sourceId(law.article("18"));
        List<Chunk> parts = store.captured.stream()
                .filter(c -> c.sourceId().startsWith(base + "#")).toList();

        assertTrue(parts.size() >= 2, "과대 조문이 분할되지 않았다: " + parts.size());
        assertTrue(parts.stream().allMatch(c -> c.content().length() <= RAGIndexer.MAX_CHARS),
                "분할 청크가 한도를 넘는다");
        assertTrue(big.startsWith(parts.get(0).content()), "첫 청크가 본문 앞부분이 아님");
        assertTrue(big.endsWith(parts.get(parts.size() - 1).content()), "마지막 청크가 본문 끝이 아님");
        int total = parts.stream().mapToInt(c -> c.content().length()).sum();
        assertTrue(total > big.length(), "오버랩이 없다(합이 원문보다 커야 함)");
        // 하위 청크도 조문 메타 유지
        assertEquals("18", parts.get(0).metadata().get("articleNo"));

        // 짧은 제104조는 분할 안 됨(단일 청크)
        assertTrue(store.captured.stream().anyMatch(c -> c.sourceId().equals(law.sourceId(law.article("104")))));
    }

    // --- fixture ---------------------------------------------------------

    private static Chunk chunk(List<Chunk> chunks, String sourceId) {
        return chunks.stream().filter(c -> c.sourceId().equals(sourceId)).findFirst()
                .orElseThrow(() -> new AssertionError("source_id 없음: " + sourceId));
    }

    private static Law law(String art18, String art104, String art2) {
        List<Article> arts = List.of(
                article("18", art18, true, ChangeType.개정),
                article("104", art104, true, ChangeType.개정),
                article("2", art2, false, ChangeType.없음));
        return new Law("001809", "283191", "주택법", Law.Status.시행예정,
                Law.AmendKind.일부개정, Law.LawType.법률, "국토교통부",
                LocalDate.of(2026, 2, 3), "21323", LocalDate.of(2026, 8, 4),
                "공포 후 6개월", Law.EnforcementType.유예,
                "[일부개정]\n◇ 개정이유 및 주요내용\n심의 효율화 …", "개정문 …",
                List.of(), arts, List.of(), null, null, "rev1", Instant.now());
    }

    private static Article article(String no, String text, boolean changed, ChangeType type) {
        return new Article(no, "제목", text, changed, type, null, null,
                LocalDate.of(2026, 8, 4), true, null);
    }
}
